package me.jhot.swiftcurrent.ui.editor

/** Plain English for JVM tests of the pure editor classes; a Robolectric test pins it to the resources. */
object EnglishEditorStrings : EditorStrings {
    override val titleFavorites = "Your favorites"
    override val titlePopup = "Edit pop-up"
    override fun titlePopupFor(label: String) = "Edit $label pop-up"
    override fun heading(section: EditorSection) = when (section) {
        EditorSection.Selected -> "Selected"
        EditorSection.Suggestions -> "Suggestions"
        EditorSection.Shortcuts -> "Shortcuts"
        EditorSection.Folders -> "Pop-up folders"
        EditorSection.Advanced -> "Advanced"
        EditorSection.Letters -> "Suggestions"
    }
    override fun hint(section: EditorSection) = when (section) {
        EditorSection.Selected -> "Nothing selected yet"
        EditorSection.Shortcuts -> "No shortcuts available"
        EditorSection.Folders -> "No pop-up folders yet"
        else -> "Suggestions appear as you use apps"
    }
    override fun action(kind: ActionKind) = when (kind) {
        ActionKind.AddShortcut -> "Add shortcut"
        ActionKind.CreateFolder -> "Create pop-up folder"
        ActionKind.SelectHiddenApps -> "Select hidden apps"
    }
    override val notInstalled = "Not installed"
    override val notShownOnHome = "Not shown on home: over the limit"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val reorder = "Reorder"
    override fun rename(label: String) = "Rename $label"
    override val newFolder = "New folder"
    override val unknownApp = "App"
    override val unknownShortcut = "Shortcut"
    override fun ownShortcuts(app: String) = "$app shortcuts"
    override fun counterOf(selected: Int, capacity: Int) = "$selected of $capacity"
    override fun selectedCount(n: Int) = "$n selected"
    override fun groupSummary(count: Int, selected: Int) = if (selected == 0) "$count shortcuts" else "$count shortcuts, $selected selected"
    override val groupExpanded = "Expanded"
    override val groupCollapsed = "Collapsed"
    override fun shownWhen(clauses: String) = "Shown when $clauses"
    override fun clausesAnd(first: String, second: String) = "$first and $second"
    override fun meldModeClause(mode: String, whenTrue: Boolean) = "Meld mode \"$mode\" is " + if (whenTrue) "active" else "inactive"
    override fun bluetoothClause(name: String, whenTrue: Boolean) = "$name is " + if (whenTrue) "connected" else "not connected"
    override fun anyBluetoothClause(whenTrue: Boolean) = if (whenTrue) "any Bluetooth device is connected" else "no Bluetooth device is connected"
    override val hiddenByConditions = "Not shown on home: conditions not met"
    override fun overLimit(counter: String) = "$counter · over the limit"
}
