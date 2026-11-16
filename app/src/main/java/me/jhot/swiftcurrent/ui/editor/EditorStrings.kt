package me.jhot.swiftcurrent.ui.editor

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import me.jhot.swiftcurrent.R

/**
 * Every word the editor shows. The pure state and row model stay free of Android: they take this,
 * the UI builds it from string resources, and JVM tests pass a plain-English fake. Interpolated
 * texts are whole format strings, never translated fragments joined together.
 */
interface EditorStrings {
    val titleFavorites: String
    val titlePopup: String
    fun titlePopupFor(label: String): String
    fun heading(section: EditorSection): String
    fun hint(section: EditorSection): String
    fun action(kind: ActionKind): String
    val notInstalled: String
    val notShownOnHome: String
    val moveUp: String
    val moveDown: String
    val reorder: String

    /** The rename button and accessibility action of one row, named by the row so a screen reader can tell them apart. */
    fun rename(label: String): String
    val newFolder: String
    val unknownApp: String
    val unknownShortcut: String
    fun ownShortcuts(app: String): String
    fun counterOf(selected: Int, capacity: Int): String
    fun selectedCount(n: Int): String

    /** The line under an app's name on its collapsible shortcut group; [count] is always 2 or more. */
    fun groupSummary(count: Int, selected: Int): String
    val groupExpanded: String
    val groupCollapsed: String
    fun overLimit(counter: String): String

    /** The line under a favorite with conditions; [clauses] is one condition phrase or several joined by [clausesAnd]. */
    fun shownWhen(clauses: String): String
    fun clausesAnd(first: String, second: String): String
    fun meldModeClause(mode: String, whenTrue: Boolean): String
    fun bluetoothClause(name: String, whenTrue: Boolean): String
    fun anyBluetoothClause(whenTrue: Boolean): String

    /** Spoken state of a favorite that stays saved but is not on home because its conditions do not hold. */
    val hiddenByConditions: String
}

class ResourceEditorStrings(private val res: Resources) : EditorStrings {
    override val titleFavorites get() = res.getString(R.string.editor_title_favorites)
    override val titlePopup get() = res.getString(R.string.editor_title_popup)
    override fun titlePopupFor(label: String) = res.getString(R.string.editor_title_popup_for, label)
    override fun heading(section: EditorSection) = res.getString(
        when (section) {
            EditorSection.Selected -> R.string.editor_section_selected
            EditorSection.Suggestions -> R.string.editor_section_suggestions
            EditorSection.Shortcuts -> R.string.editor_section_shortcuts
            EditorSection.Folders -> R.string.editor_section_folders
            EditorSection.Advanced -> R.string.editor_section_advanced
            // The letter headers are the letters themselves, never looked up here.
            EditorSection.Letters -> R.string.editor_section_suggestions
        },
    )
    override fun hint(section: EditorSection) = res.getString(
        when (section) {
            EditorSection.Selected -> R.string.editor_hint_selected
            EditorSection.Shortcuts -> R.string.editor_hint_shortcuts
            EditorSection.Folders -> R.string.editor_hint_folders
            else -> R.string.editor_hint_suggestions
        },
    )
    override fun action(kind: ActionKind) = res.getString(
        when (kind) {
            ActionKind.AddShortcut -> R.string.editor_action_add_shortcut
            ActionKind.CreateFolder -> R.string.editor_action_create_folder
            ActionKind.SelectHiddenApps -> R.string.editor_action_select_hidden_apps
        },
    )
    override val notInstalled get() = res.getString(R.string.editor_not_installed)
    override val notShownOnHome get() = res.getString(R.string.editor_not_shown_on_home)
    override val moveUp get() = res.getString(R.string.editor_move_up)
    override val moveDown get() = res.getString(R.string.editor_move_down)
    override val reorder get() = res.getString(R.string.editor_reorder)
    override fun rename(label: String) = res.getString(R.string.editor_rename_item, label)
    override val newFolder get() = res.getString(R.string.editor_new_folder)
    override val unknownApp get() = res.getString(R.string.label_unknown_app)
    override val unknownShortcut get() = res.getString(R.string.label_unknown_shortcut)
    override fun ownShortcuts(app: String) = res.getString(R.string.editor_own_shortcuts, app)
    override fun counterOf(selected: Int, capacity: Int) = res.getString(R.string.editor_counter_of, selected, capacity)
    override fun selectedCount(n: Int) = res.getQuantityString(R.plurals.editor_selected_count, n, n)
    override fun groupSummary(count: Int, selected: Int) =
        if (selected == 0) res.getString(R.string.editor_group_count, count) else res.getString(R.string.editor_group_count_selected, count, selected)
    override val groupExpanded get() = res.getString(R.string.editor_group_expanded)
    override val groupCollapsed get() = res.getString(R.string.editor_group_collapsed)
    override fun overLimit(counter: String) = res.getString(R.string.editor_counter_over_limit, counter)
    override fun shownWhen(clauses: String) = res.getString(R.string.editor_shown_when, clauses)
    override fun clausesAnd(first: String, second: String) = res.getString(R.string.editor_clauses_and, first, second)
    override fun meldModeClause(mode: String, whenTrue: Boolean) =
        res.getString(if (whenTrue) R.string.editor_clause_meld_active else R.string.editor_clause_meld_inactive, mode)
    override fun bluetoothClause(name: String, whenTrue: Boolean) =
        res.getString(if (whenTrue) R.string.editor_clause_bt_connected else R.string.editor_clause_bt_not_connected, name)
    override fun anyBluetoothClause(whenTrue: Boolean) =
        res.getString(if (whenTrue) R.string.editor_clause_any_bt_connected else R.string.editor_clause_no_bt_connected)
    override val hiddenByConditions get() = res.getString(R.string.editor_hidden_by_conditions)
}

/**
 * Stable across configuration changes on purpose: [ResourceEditorStrings] reads the live resources on
 * every call, so a new language shows up without a new instance, and a new instance would make the
 * editor rebuild its state. Reading the configuration still recomposes the caller when it changes.
 */
@Composable
fun rememberEditorStrings(): EditorStrings {
    val context = LocalContext.current
    LocalConfiguration.current
    return remember(context) { ResourceEditorStrings(context.resources) }
}
