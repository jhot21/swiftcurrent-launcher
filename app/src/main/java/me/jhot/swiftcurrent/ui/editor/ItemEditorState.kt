package me.jhot.swiftcurrent.ui.editor

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Renames
import me.jhot.swiftcurrent.core.Target
import java.util.UUID

enum class Mode { Favorites, Popup }

data class ShortcutCandidate(val key: String, val label: String, val appLabel: String)

/** What the editor can offer beyond the current selection. Folders are the ones that already exist. */
data class Candidates(
    val suggestions: List<AppInfo> = emptyList(),
    val apps: List<Section> = emptyList(),
    val shortcuts: List<ShortcutCandidate> = emptyList(),
    val folders: List<Entry> = emptyList(),
)

/**
 * The selection of one editor, over favorites or over a pop-up. Every change is reported through
 * [onChange] at once, so the caller persists live; there is no cancel step.
 *
 * - [capacity] is the most favorites home can draw, or null in a pop-up. It does not gate adding: a
 *   list past it keeps every entry, and [overLimit] and [firstHiddenIndex] flag it.
 * - A favorite whose app is gone ([isMissing]), or whose conditions do not hold right now
 *   ([isInactive]), stays in the saved list, but home skips it, so it is not counted and does not
 *   use one of the drawn slots ([counter], [overLimit] and [firstHiddenIndex] account for that).
 * - In a pop-up the app's own shortcuts are one movable row. [ownShortcutsApp] is its package (null
 *   when the host is not an app); [ownShortcutsIndex] is its place among the rows, or null when the
 *   stored pop-up left it out (hidden).
 * - Folders are a library: [folders] is every folder available to pick, the ones already in
 *   [Candidates.folders] plus any folder in [selected] (data saved before the library existed).
 *   Unticking a folder only removes it from the selection; it stays in [folders] with its popup.
 *   Folders nested inside another pop-up are snapshots (copies), a known limitation: editing a
 *   folder later does not update the copy in a pop-up that holds it.
 * - Row keys: ids may repeat in stored data, so nothing here assumes they are unique; the list
 *   keys come from [EditorSections.uniqueKeys].
 */
class ItemEditorState(
    selected: List<Entry>,
    val candidates: Candidates,
    val capacity: Int?,
    val strings: EditorStrings,
    val ownShortcutsApp: String? = null,
    ownShortcutsIndex: Int? = null,
    private val isMissing: (Entry) -> Boolean = { false },
    /** True for an entry whose conditions are not met right now, so home is not showing it. */
    private val isInactive: (Entry) -> Boolean = { false },
    /** The owner's app names by component, for an app that is no longer installed (the candidates only carry installed ones). */
    private val appNames: Map<String, String> = emptyMap(),
    private val newId: () -> String = { "folder-" + UUID.randomUUID() },
    private val onChange: (List<PopupRow>) -> Unit = {},
) {
    private val slots = mutableStateListOf<PopupRow>()
    private val folderList = mutableStateListOf<Entry>().apply {
        addAll(candidates.folders)
        selected.filter { it.target == Target.Folder && none { f -> f.id == it.id } }.forEach { add(it) }
    }
    private val addedShortcuts = mutableStateListOf<ShortcutCandidate>()
    private var restoreAt by mutableStateOf(0)

    /** True while the own-shortcuts row is left out of the pop-up. */
    var ownShortcutsHidden by mutableStateOf(ownShortcutsApp != null && ownShortcutsIndex == null)
        private set

    private val appLabels: Map<String, AppInfo> =
        (candidates.apps.flatMap { it.apps } + candidates.suggestions).associateBy { it.component }

    init {
        slots.addAll(selected.map { PopupRow.Item(it) })
        if (ownShortcutsApp != null && ownShortcutsIndex != null) {
            slots.add(ownShortcutsIndex.coerceIn(0, slots.size), PopupRow.OwnShortcuts(ownShortcutsApp))
        }
    }

    val rows: List<PopupRow> get() = slots.toList()

    /** Same as `rows.size` without copying the list; every selected row asks for it. */
    val rowCount: Int get() = slots.size

    // Which shortcut groups start open: the apps that already have a selected shortcut, so a selection is never hidden.
    private val openAtStart: Set<String> = run {
        val keys = selected.mapNotNull { (it.target as? Target.Shortcut)?.key }.toSet()
        candidates.shortcuts.filter { it.key in keys }.map { it.appLabel }.toSet()
    }

    // What the owner flipped by hand. Held here, not on disk: it survives recomposition but not a new editor.
    private val groupOverrides = mutableStateMapOf<String, Boolean>()

    fun isGroupOpen(app: String): Boolean = groupOverrides[app] ?: (app in openAtStart)

    fun toggleGroup(app: String) {
        groupOverrides[app] = !isGroupOpen(app)
    }

    /** Every shortcut on offer: the ones the editor opened with plus any made while it is open. */
    val shortcutCandidates: List<ShortcutCandidate>
        get() = candidates.shortcuts + addedShortcuts.filter { a -> candidates.shortcuts.none { it.key == a.key } }

    /** Offers a shortcut that did not exist when the editor opened (one just made). It is not selected. */
    fun addShortcutCandidate(c: ShortcutCandidate) {
        if (shortcutCandidates.none { it.key == c.key }) addedShortcuts.add(c)
    }

    /** The item rows only: the favorites list, or a pop-up's entries. */
    val entries: List<Entry> get() = slots.filterIsInstance<PopupRow.Item>().map { it.entry }

    val folders: List<Entry> get() = folderList.toList()

    private fun shown(e: Entry) = !isMissing(e) && !isInactive(e)

    /** How many entries home can show right now: the ones that are neither missing nor condition-inactive. */
    private val shownCount: Int get() = entries.count(::shown)

    val counter: String
        get() = if (capacity == null) strings.selectedCount(slots.size) else strings.counterOf(shownCount, capacity)

    val overLimit: Boolean get() = capacity != null && shownCount > capacity

    /** Index in [rows] of the first entry home will not draw, or null when all fit. */
    val firstHiddenIndex: Int?
        get() {
            val cap = capacity ?: return null
            var drawn = 0
            slots.forEachIndexed { i, row ->
                if (row !is PopupRow.Item || !shown(row.entry)) return@forEachIndexed
                if (drawn == cap) return i
                drawn++
            }
            return null
        }

    fun isSelected(e: Entry): Boolean = slots.any { it is PopupRow.Item && sameItem(it.entry, e) }

    fun missing(e: Entry): Boolean = isMissing(e)

    /** True while the entry's conditions keep it off home; the list draws it dimmed. */
    fun isDimmed(e: Entry): Boolean = isInactive(e)

    /** Names given to apps in this editor session, by component; a blank one means the system name again. */
    private val renamedApps = mutableStateMapOf<String, String>()

    /**
     * What the entry is called. An app has one name wherever it appears: the one the host built the list
     * with (the owner's name, if any), unless it was renamed in this session. Shortcuts and folders are named per entry.
     */
    fun labelOf(e: Entry): String = when (val t = e.target) {
        is Target.App -> {
            val renamed = renamedApps[t.component]
            if (renamed != null) renamed.ifBlank { null } ?: defaultLabelOf(e)
            else appLabels[t.component]?.label ?: appNames[t.component]?.ifBlank { null } ?: e.customLabel?.ifBlank { null } ?: strings.unknownApp
        }
        else -> e.customLabel?.ifBlank { null } ?: defaultLabelOf(e)
    }

    /** What the entry is called when it has no custom label: the real app or shortcut name, or the folder's default. */
    fun defaultLabelOf(e: Entry): String = when (val t = e.target) {
        is Target.App -> appLabels[t.component]?.systemLabel ?: strings.unknownApp
        is Target.Shortcut -> shortcutCandidates.firstOrNull { it.key == t.key }?.label ?: strings.unknownShortcut
        Target.Folder -> strings.newFolder
    }

    /**
     * Follows a rename the host has already saved (through [Renames.apply], which is the one set of
     * rules), so the list shows it at once and the next write does not put the old label back. It
     * does not call [onChange]: the host's own write is the save.
     */
    fun applyRename(entryId: String, label: String?, isFolder: Boolean? = null) {
        val items = slots.filterIsInstance<PopupRow.Item>().map { it.entry }
        // An app's name is shared, so remember it for every row that shows the same app.
        (items.firstOrNull { it.id == entryId }?.target as? Target.App)?.let { renamedApps[it.component] = Renames.sanitize(label).orEmpty() }
        val renamed = Renames.apply(LauncherData(favorites = items, folders = folderList.toList()), entryId, label, isFolder = isFolder)
        var k = 0
        slots.forEachIndexed { i, row ->
            if (row is PopupRow.Item) {
                val next = renamed.favorites[k++]
                if (next != row.entry) slots[i] = PopupRow.Item(next)
            }
        }
        renamed.folders.forEachIndexed { i, f -> if (f != folderList[i]) folderList[i] = f }
    }

    /**
     * Follows conditions the host has already saved for a selected row, so the next write carries them
     * instead of the stale entry. Like [applyRename] it does not call [onChange]. Only the row changes:
     * a folder's library entry never carries conditions.
     */
    fun applyConditions(entryId: String, conditions: List<Condition>) {
        slots.forEachIndexed { i, row ->
            if (row is PopupRow.Item && row.entry.id == entryId) slots[i] = PopupRow.Item(row.entry.copy(conditions = conditions))
        }
    }

    fun ownShortcutsLabel(): String {
        val pkg = ownShortcutsApp ?: return ""
        val name = appLabels.values.firstOrNull { it.packageName == pkg }?.label ?: strings.unknownApp
        return strings.ownShortcuts(name)
    }

    /** Selects an unselected entry or removes a selected one. Neither is limited by [capacity]. */
    fun toggle(e: Entry) {
        val at = slots.indexOfFirst { it is PopupRow.Item && sameItem(it.entry, e) }
        if (at >= 0) {
            slots.removeAt(at)
        } else {
            slots.add(PopupRow.Item(e))
        }
        changed()
    }

    /** The row being dragged and how it is placed, so the rows it passes can step aside while it is still down. */
    class Drag(val from: Int, val offsetPx: Float, val pitchPx: Float, val count: Int) {
        val target: Int get() = ReorderMath.targetIndex(from, offsetPx, pitchPx, count)
    }

    var drag by mutableStateOf<Drag?>(null)

    /** Where a drag is and where it would drop. */
    data class DragPlacement(val from: Int, val target: Int, val pitchPx: Float)

    /**
     * What the rows need from [drag]. [drag] is replaced on every pointer move, but this only changes when the
     * drop target does, so the rows recompose per slot crossed rather than per frame.
     */
    val dragPlacement: DragPlacement? by derivedStateOf { drag?.let { DragPlacement(it.from, it.target, it.pitchPx) } }

    fun move(from: Int, to: Int) {
        if (from !in slots.indices || to !in slots.indices || from == to) return
        slots.add(to, slots.removeAt(from))
        changed()
    }

    /** Hides the own-shortcuts row, or puts it back where it was. No-op when the host has no app. */
    fun toggleOwnShortcuts() {
        val app = ownShortcutsApp ?: return
        if (ownShortcutsHidden) {
            slots.add(restoreAt.coerceIn(0, slots.size), PopupRow.OwnShortcuts(app))
            ownShortcutsHidden = false
        } else {
            val at = slots.indexOfFirst { it is PopupRow.OwnShortcuts }
            if (at < 0) return
            restoreAt = at
            slots.removeAt(at)
            ownShortcutsHidden = true
        }
        changed()
    }

    /** Makes a new empty folder available (and reported, so it is saved) and selects it. */
    fun createFolder(): Entry {
        val folder = Entry(newId(), Target.Folder, popup = Popup(emptyList()))
        folderList.add(folder)
        toggle(folder)
        return folder
    }

    private fun changed() = onChange(rows)

    // Candidates use the component or shortcut key as their id, saved favorites may have any id, so
    // identity is the target; folders have none, so theirs is the id.
    private fun sameItem(a: Entry, b: Entry): Boolean =
        if (a.target is Target.Folder || b.target is Target.Folder) {
            a.target is Target.Folder && b.target is Target.Folder && a.id == b.id
        } else {
            a.target == b.target
        }
}
