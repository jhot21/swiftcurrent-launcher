package me.jhot.swiftcurrent.ui.settings

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.HapticStrength
import me.jhot.swiftcurrent.core.HiddenContactAction
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target

/** Pure partial writes for `LauncherStore.update`, so each settings change is one atomic step. */
object SettingsEdits {
    fun setSortByUsage(d: LauncherData, on: Boolean): LauncherData =
        d.copy(settings = d.settings.copy(sortByUsage = on))

    fun setHaptics(d: LauncherData, on: Boolean): LauncherData =
        d.copy(settings = d.settings.copy(haptics = on))

    fun setScrubHaptic(d: LauncherData, strength: Int): LauncherData =
        d.copy(settings = d.settings.copy(scrubHaptic = HapticStrength.clamp(strength)))

    fun setGestureHaptic(d: LauncherData, strength: Int): LauncherData =
        d.copy(settings = d.settings.copy(gestureHaptic = HapticStrength.clamp(strength)))

    fun setAppsFirst(d: LauncherData, on: Boolean): LauncherData =
        d.copy(settings = d.settings.copy(appsFirst = on))

    fun setContactsFirst(d: LauncherData, on: Boolean): LauncherData =
        d.copy(settings = d.settings.copy(contactsFirst = on))

    fun setLearnFromPicks(d: LauncherData, on: Boolean): LauncherData =
        d.copy(settings = d.settings.copy(learnFromPicks = on))

    fun hideContactAction(d: LauncherData, h: HiddenContactAction): LauncherData {
        val kept = d.settings.hiddenContactActions
        if (kept.any { it.contact == h.contact && it.action == h.action }) return d
        return d.copy(settings = d.settings.copy(hiddenContactActions = kept + h))
    }

    fun showContactAction(d: LauncherData, contact: String, action: String): LauncherData =
        d.copy(settings = d.settings.copy(hiddenContactActions = d.settings.hiddenContactActions.filterNot { it.contact == contact && it.action == action }))

    fun setHidden(d: LauncherData, component: String, hidden: Boolean): LauncherData =
        d.copy(hiddenApps = if (hidden) d.hiddenApps + component else d.hiddenApps - component)

    /**
     * Removes a folder from the library and from everywhere it is used: favorites and pop-up rows,
     * nested pop-ups too. Anything else in those pop-ups stays, as does an app's own-shortcuts row.
     */
    fun deleteFolder(d: LauncherData, id: String): LauncherData {
        fun isIt(e: Entry) = e.id == id && e.target == Target.Folder
        fun clean(entries: List<Entry>): List<Entry> = entries.filterNot(::isIt).map { e ->
            val popup = e.popup ?: return@map e
            e.copy(
                popup = Popup(
                    popup.rows.mapNotNull { row ->
                        when {
                            row is PopupRow.Item && isIt(row.entry) -> null
                            row is PopupRow.Item -> PopupRow.Item(clean(listOf(row.entry)).single())
                            else -> row
                        }
                    },
                ),
            )
        }
        return d.copy(favorites = clean(d.favorites), folders = clean(d.folders))
    }
}
