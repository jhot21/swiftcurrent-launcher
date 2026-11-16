package me.jhot.swiftcurrent.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Every page inside Settings. Each has one parent, so the page stack is just the chain of parents. */
enum class SettingsRoute(val parent: SettingsRoute?) {
    Root(null),
    Home(Root), Apps(Root), Search(Root), Gestures(Root), Advanced(Root),
    Calendars(Home), Spacing(Home),
    HiddenApps(Apps), PopupFolders(Apps),
    Shortcuts(Search), HiddenContactMethods(Search), SearchCalendars(Search),
}

/** The topics the root lists, in order. */
val SETTINGS_TOPICS: List<SettingsRoute> =
    listOf(SettingsRoute.Home, SettingsRoute.Apps, SettingsRoute.Search, SettingsRoute.Gestures, SettingsRoute.Advanced)

/**
 * Where Settings is. Back goes to the parent page; [back] returns false from the root, where the
 * caller leaves Settings for whatever opened it. Opening a page at [start] (the editor sends the
 * owner straight to hidden apps) still has its parents behind it.
 */
class SettingsNav(start: SettingsRoute = SettingsRoute.Root) {
    private val _current = MutableStateFlow(start)
    val current: StateFlow<SettingsRoute> = _current.asStateFlow()

    fun open(route: SettingsRoute) {
        _current.value = route
    }

    fun back(): Boolean {
        val parent = _current.value.parent ?: return false
        _current.value = parent
        return true
    }
}
