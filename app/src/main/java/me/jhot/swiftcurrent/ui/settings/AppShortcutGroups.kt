package me.jhot.swiftcurrent.ui.settings

import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.userShortcutKey

data class SystemShortcutRow(val key: String, val label: String, val enabled: Boolean)

data class UserShortcutRow(val id: String, val key: String, val label: String, val enabled: Boolean)

/**
 * One app's page: the shortcuts the app publishes ([fromApp]) and the ones the owner made
 * ([userMade]) are separate lists, so each can be shown, deleted and toggled under its own heading.
 */
data class AppShortcutGroup(
    val packageName: String,
    val appLabel: String,
    val fromApp: List<SystemShortcutRow>,
    val userMade: List<UserShortcutRow>,
    val creators: List<Creator>,
) {
    val count: Int get() = fromApp.size + userMade.size

    /** Every shortcut is on; "toggle all" then offers to turn them off, otherwise to turn them on. */
    val allOn: Boolean get() = fromApp.all { it.enabled } && userMade.all { it.enabled }

    /** Every key a "toggle all" touches. */
    val keys: List<String> get() = fromApp.map { it.key } + userMade.map { it.key }
}

enum class SourceId { Apps, Shortcuts, Contacts }

/** A search source in the list; [missingPermission] puts the "Missing permission" badge on it. */
data class SourceRow(val id: SourceId, val missingPermission: Boolean)

object AppShortcutGroups {
    /**
     * Groups by package, sorted by app label. An app appears when it publishes shortcuts, has
     * shortcuts the owner made, or offers a way to make some. Rows keep the order they came in.
     */
    fun build(
        system: List<ShortcutEntry>,
        userMade: List<UserShortcut>,
        creators: List<Creator>,
        disabled: Set<String>,
        appLabel: (String) -> String,
    ): List<AppShortcutGroup> {
        // One pass per input, so a phone with many apps and shortcuts does not filter the whole
        // list once per package.
        val systemBy = system.groupBy { it.packageName }
        val userBy = userMade.groupBy { it.appPackage }
        val creatorsBy = creators.groupBy { it.packageName }
        val packages = (systemBy.keys + userBy.keys + creatorsBy.keys).distinct()
        return packages.map { pkg ->
            AppShortcutGroup(
                packageName = pkg,
                appLabel = appLabel(pkg),
                fromApp = systemBy[pkg].orEmpty().map { SystemShortcutRow(it.key, it.label, it.key !in disabled) },
                userMade = userBy[pkg].orEmpty().map {
                    val key = userShortcutKey(it.id)
                    UserShortcutRow(it.id, key, it.label, key !in disabled)
                },
                creators = creatorsBy[pkg].orEmpty(),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel })
    }

    /** Contacts needs the permission; shortcuts need the app to be the default home app. */
    fun sources(contactsGranted: Boolean, shortcutsGranted: Boolean = true): List<SourceRow> = listOf(
        SourceRow(SourceId.Apps, missingPermission = false),
        SourceRow(SourceId.Shortcuts, missingPermission = !shortcutsGranted),
        SourceRow(SourceId.Contacts, missingPermission = !contactsGranted),
    )
}
