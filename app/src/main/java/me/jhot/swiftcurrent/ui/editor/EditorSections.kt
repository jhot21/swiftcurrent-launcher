package me.jhot.swiftcurrent.ui.editor

import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target

/** The scrubber's jump icons, in the order they run down the strip. */
enum class EditorIcon { Selected, Suggestions, Hash, Shortcuts, Folders, Advanced }

enum class EditorSection { Selected, Suggestions, Letters, Shortcuts, Folders, Advanced }

enum class ActionKind { AddShortcut, CreateFolder, SelectHiddenApps }

/** One line of the editor's scrolling list, so the layout, the scrubber targets and the tests share one model. */
sealed interface EditorRow {
    val key: String

    data class Heading(val section: EditorSection) : EditorRow {
        override val key get() = "h-$section"
    }

    data class Letter(val ch: Char) : EditorRow {
        override val key get() = "l-$ch"
    }

    data class Selected(val row: PopupRow, val index: Int) : EditorRow {
        override val key
            get() = when (row) {
                is PopupRow.Item -> "s-${row.entry.id}"
                is PopupRow.OwnShortcuts -> "s-own"
            }
    }

    /** Where the saved list passes what home can draw; rows after it are kept but not shown. */
    data object Boundary : EditorRow {
        override val key get() = "boundary"
    }

    data class Hint(val section: EditorSection) : EditorRow {
        override val key get() = "hint-$section"
    }

    /** An app's collapsible group of shortcuts; the rows under it are [Pick]s with [Pick.grouped] set while it is [open]. */
    data class AppGroup(val app: String, val count: Int, val selected: Int, val open: Boolean) : EditorRow {
        override val key get() = "g-$app"
    }

    data class Pick(
        val section: EditorSection,
        val entry: Entry,
        val label: String,
        val subtitle: String?,
        /** Inside an open [AppGroup]: drawn indented, and the group header already names the app. */
        val grouped: Boolean = false,
    ) : EditorRow {
        override val key get() = "p-$section-${entry.id}"
    }

    /** The own-shortcuts row while it is hidden, so it can be brought back. */
    data class OwnPick(val app: String) : EditorRow {
        override val key get() = "own-pick"
    }

    data class Action(val kind: ActionKind) : EditorRow {
        override val key get() = "a-$kind"
    }
}

object EditorSections {
    // Identifiers only; the scrubber draws these as icons. Distinct from every letter and '#'.
    const val SELECTED = '✓'
    const val SUGGESTIONS = '✦'
    const val SHORTCUTS = '⚓'
    const val FOLDERS = '◇'
    const val ADVANCED = '+'

    fun appEntry(app: AppInfo) = Entry(app.component, Target.App(app.component))

    // The label is saved with the entry so Home can show it without asking the shortcut service.
    fun shortcutEntry(s: ShortcutCandidate) = Entry(s.key, Target.Shortcut(s.key), customLabel = s.label)

    class ShortcutGroup(val app: String, val shortcuts: List<ShortcutCandidate>)

    /** Apps in label order (as the settings page lists them), each app's shortcuts in the order the system gave them. */
    fun groupShortcuts(shortcuts: List<ShortcutCandidate>): List<ShortcutGroup> =
        shortcuts.groupBy { it.appLabel }.map { ShortcutGroup(it.key, it.value) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.app })

    fun rows(state: ItemEditorState): List<EditorRow> = buildList {
        val c = state.candidates
        add(EditorRow.Heading(EditorSection.Selected))
        if (state.rows.isEmpty()) add(EditorRow.Hint(EditorSection.Selected))
        val boundary = state.firstHiddenIndex
        state.rows.forEachIndexed { i, row ->
            if (i == boundary) add(EditorRow.Boundary)
            add(EditorRow.Selected(row, i))
        }

        add(EditorRow.Heading(EditorSection.Suggestions))
        val own = state.ownShortcutsApp
        if (own != null && state.ownShortcutsHidden) add(EditorRow.OwnPick(own))
        c.suggestions.forEach { add(EditorRow.Pick(EditorSection.Suggestions, appEntry(it), it.label, null)) }
        if (c.suggestions.isEmpty() && !(own != null && state.ownShortcutsHidden)) {
            add(EditorRow.Hint(EditorSection.Suggestions))
        }

        c.apps.forEach { section ->
            add(EditorRow.Letter(section.key))
            section.apps.forEach { add(EditorRow.Pick(EditorSection.Letters, appEntry(it), it.label, null)) }
        }

        add(EditorRow.Heading(EditorSection.Shortcuts))
        val shortcuts = state.shortcutCandidates
        groupShortcuts(shortcuts).forEach { group ->
            val only = group.shortcuts.singleOrNull()
            if (only != null) {
                // A group of one is just an extra tap, so it stays a plain row that names its app.
                add(EditorRow.Pick(EditorSection.Shortcuts, shortcutEntry(only), only.label, only.appLabel))
                return@forEach
            }
            val open = state.isGroupOpen(group.app)
            add(EditorRow.AppGroup(group.app, group.shortcuts.size, group.shortcuts.count { state.isSelected(shortcutEntry(it)) }, open))
            if (open) group.shortcuts.forEach { add(EditorRow.Pick(EditorSection.Shortcuts, shortcutEntry(it), it.label, null, grouped = true)) }
        }
        if (shortcuts.isEmpty()) add(EditorRow.Hint(EditorSection.Shortcuts))

        add(EditorRow.Heading(EditorSection.Folders))
        state.folders.forEach { add(EditorRow.Pick(EditorSection.Folders, it, state.labelOf(it), null)) }
        if (state.folders.isEmpty()) add(EditorRow.Hint(EditorSection.Folders))

        add(EditorRow.Heading(EditorSection.Advanced))
        ActionKind.entries.forEach { add(EditorRow.Action(it)) }
    }

    /** One key per row, unique even when the data repeats an id, so the lazy list can never throw on it. */
    fun uniqueKeys(rows: List<EditorRow>): List<String> {
        val seen = HashMap<String, Int>()
        return rows.map { row ->
            val n = seen.merge(row.key, 1, Int::plus)!!
            if (n == 1) row.key else "${row.key}#$n"
        }
    }

    private fun headingAt(section: EditorSection, rows: List<EditorRow>) =
        rows.indexOfFirst { it is EditorRow.Heading && it.section == section }

    /** Index in [rows] the icon scrolls to. `#` falls back to the first letter, then to Shortcuts. */
    fun indexFor(icon: EditorIcon, rows: List<EditorRow>): Int = when (icon) {
        EditorIcon.Selected -> headingAt(EditorSection.Selected, rows)
        EditorIcon.Suggestions -> headingAt(EditorSection.Suggestions, rows)
        EditorIcon.Hash -> indexForChar('#', rows)
        EditorIcon.Shortcuts -> headingAt(EditorSection.Shortcuts, rows)
        EditorIcon.Folders -> headingAt(EditorSection.Folders, rows)
        EditorIcon.Advanced -> headingAt(EditorSection.Advanced, rows)
    }

    /** A letter's header, or the next letter that has apps, or the last one; Shortcuts when there are none. */
    fun indexForChar(c: Char, rows: List<EditorRow>): Int {
        val letters = rows.withIndex().filter { it.value is EditorRow.Letter }
        if (letters.isEmpty()) return headingAt(EditorSection.Shortcuts, rows)
        val exact = letters.firstOrNull { (it.value as EditorRow.Letter).ch == c }
        val next = letters.firstOrNull { (it.value as EditorRow.Letter).ch > c }
        return (exact ?: next ?: letters.last()).index
    }

    /** What a scrubber character stands for, or null for a letter. */
    fun iconOf(c: Char): EditorIcon? = when (c) {
        SELECTED -> EditorIcon.Selected
        SUGGESTIONS -> EditorIcon.Suggestions
        '#' -> EditorIcon.Hash
        SHORTCUTS -> EditorIcon.Shortcuts
        FOLDERS -> EditorIcon.Folders
        ADVANCED -> EditorIcon.Advanced
        else -> null
    }

    /** Check, sparkle, `#`, the letters that have apps, then shortcuts, folders and advanced. `#` always stays. */
    fun letters(sections: List<Section>): List<Char> = buildList {
        add(SELECTED)
        add(SUGGESTIONS)
        add('#')
        sections.forEach { if (it.key != '#') add(it.key) }
        add(SHORTCUTS)
        add(FOLDERS)
        add(ADVANCED)
    }

    /** Scrubber character to row index; `#` and the icons use [indexFor]. */
    fun indexForScrub(c: Char, rows: List<EditorRow>): Int =
        iconOf(c)?.let { indexFor(it, rows) } ?: indexForChar(c, rows)
}
