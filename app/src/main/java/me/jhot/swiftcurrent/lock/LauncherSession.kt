package me.jhot.swiftcurrent.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Screen { Home, AppList, Search, Settings, Editor, Setup }

/** How setup was opened: by the first-run check (Done/Skip mark it seen) or from Settings (they just go back). */
enum class SetupOrigin { FirstRun, Settings }

/**
 * Where the launcher is, kept for the process. A screen-off always returns to [Screen.Home] and
 * bumps [resetToken]; the shell keys list state, sheets and pop-ups on the token so they are
 * discarded, not just hidden.
 */
class LauncherSession {
    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _resetToken = MutableStateFlow(0)
    val resetToken: StateFlow<Int> = _resetToken.asStateFlow()

    /** Kept here, not in composition, so it survives an activity recreation (dark mode, font size, language). */
    @Volatile
    var setupOrigin: SetupOrigin = SetupOrigin.FirstRun
        private set

    fun openSetup(origin: SetupOrigin) {
        setupOrigin = origin
        _screen.value = Screen.Setup
    }

    fun navigate(to: Screen) {
        _screen.value = to
    }

    /** Home on Home opens search; Home from anywhere else returns to Home. */
    fun onHomePressed() {
        _screen.value = if (_screen.value == Screen.Home) Screen.Search else Screen.Home
    }

    fun onScreenOff() {
        _screen.value = Screen.Home
        _resetToken.update { it + 1 }
    }
}
