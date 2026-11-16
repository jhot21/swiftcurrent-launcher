package me.jhot.swiftcurrent.core

/** Adding and removing an app as a favorite from outside the editor; pure so the rules are tested without Android. */
object Favorites {
    /** The top-level favorite for [component], if there is one. */
    fun entryFor(data: LauncherData, component: String): Entry? =
        data.favorites.firstOrNull { (it.target as? Target.App)?.component == component }

    /**
     * What the sheet for an app shows and edits: its favorite entry when it has one, otherwise a stand-in
     * (its component is its id) carrying the pop-up stored for it in [LauncherData.appPopups], if any.
     */
    fun menuEntry(data: LauncherData, component: String): Entry =
        entryFor(data, component) ?: Entry(component, Target.App(component), popup = data.appPopups[component])

    fun isFavorite(data: LauncherData, component: String): Boolean = entryFor(data, component) != null

    /**
     * Appends the app the way the editor does (its component is its id). An app that is already a
     * favorite is left where it is, so tapping twice never doubles it.
     */
    fun add(data: LauncherData, component: String): LauncherData {
        if (isFavorite(data, component)) return data
        // The pop-up the app had as a non-favorite moves onto the entry: it lives in one place at a time.
        return data.copy(
            favorites = data.favorites + Entry(component, Target.App(component), popup = data.appPopups[component]),
            appPopups = data.appPopups - component,
        )
    }

    /**
     * Removes the app from the favorites; a folder it is in keeps it. Its pop-up stays for its sheet, moved
     * to [LauncherData.appPopups], so unstarring never loses what was built there.
     */
    fun remove(data: LauncherData, component: String): LauncherData {
        val entry = entryFor(data, component) ?: return data
        return data.copy(
            favorites = data.favorites.filterNot { (it.target as? Target.App)?.component == component },
            appPopups = entry.popup?.let { data.appPopups + (component to it) } ?: data.appPopups,
        )
    }
}
