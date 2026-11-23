package me.jhot.swiftcurrent.shell

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.ui.settings.SettingsNav
import me.jhot.swiftcurrent.ui.settings.SettingsRoute

sealed interface Overlay {
    data object None : Overlay

    /**
     * [anchor]: the bounds of the item it came from, in root coordinates, so the sheet can line its
     * first line up with it. [below] is the pop-up this one was opened over, if any: closing this one returns to it.
     */
    data class Popup(val entry: Entry, val anchor: Rect? = null, val below: Popup? = null) : Overlay

    /** [fromList]: opened from the app list, where the sheet offers add/remove favorite instead of editing them all. */
    data class ActionSheet(val entry: Entry, val fromList: Boolean = false) : Overlay

    /** The rename sheet for a Home item; the editor shows its own for its rows. */
    data class Rename(val entry: Entry) : Overlay
    data object Agenda : Overlay
}

/** What the editor screen is editing; held by the shell while [me.jhot.swiftcurrent.lock.Screen.Editor] shows. */
sealed interface EditorTarget {
    data object Favorites : EditorTarget
    data class Popup(val entryId: String) : EditorTarget
}

/**
 * Overlay and message state of the shell, pure so it is testable without Compose. Which screen is
 * showing lives in the session, not here. At most one overlay is open: opening another replaces
 * it, except that a pop-up opened over a pop-up stacks on it (see [Overlay.Popup.below]).
 */
class ShellState {
    private val _overlay = MutableStateFlow<Overlay>(Overlay.None)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    private val _message = MutableStateFlow<ShellMessage?>(null)
    val message: StateFlow<ShellMessage?> = _message.asStateFlow()

    // Bumped on every post so the same message shown twice restarts its auto-dismiss timer.
    private val _messageSerial = MutableStateFlow(0)
    val messageSerial: StateFlow<Int> = _messageSerial.asStateFlow()

    private val _editorTarget = MutableStateFlow<EditorTarget?>(null)
    val editorTarget: StateFlow<EditorTarget?> = _editorTarget.asStateFlow()

    // Kept here, not in composition, so an activity recreation does not drop the owner's place in Settings.
    private var settingsNav: SettingsNav? = null
    private var settingsStart = SettingsRoute.Root
    private var settingsFromSearch = false

    /** Where Settings opens next: its front page from the app list, a deeper page when something sends the owner there. */
    fun openSettings(start: SettingsRoute, fromSearch: Boolean = false) {
        settingsStart = start
        settingsFromSearch = fromSearch
        settingsNav = SettingsNav(start, exitAtStart = fromSearch)
    }

    /** Where leaving Settings goes: Home when it was opened from search, else the app list. */
    fun settingsExit(): Screen = if (settingsFromSearch) Screen.Home else Screen.AppList

    /** The page stack of the Settings visit in progress. */
    fun settingsNav(): SettingsNav =
        settingsNav ?: SettingsNav(settingsStart, exitAtStart = settingsFromSearch).also { settingsNav = it }

    /** Bumped on each visit from Home so the app list starts at the top; a recreation does not bump it. */
    var listVisit = 0
        private set

    fun nextListVisit() {
        listVisit++
    }

    fun openEditor(target: EditorTarget) {
        _overlay.value = Overlay.None
        _editorTarget.value = target
    }

    fun closeEditor() {
        _editorTarget.value = null
    }

    fun openPopup(entry: Entry, anchor: Rect? = null) {
        _overlay.value = Overlay.Popup(entry, anchor, below = _overlay.value as? Overlay.Popup)
    }

    fun openActionSheet(entry: Entry, fromList: Boolean = false) {
        _overlay.value = Overlay.ActionSheet(entry, fromList)
    }

    fun openRename(entry: Entry) {
        _overlay.value = Overlay.Rename(entry)
    }

    fun openAgenda() {
        _overlay.value = Overlay.Agenda
    }

    fun dismissOverlay() {
        _overlay.value = Overlay.None
    }

    /** Closes only the top pop-up of a stack, returning to the one beneath; anything else closes outright. */
    fun dismissTop() {
        _overlay.value = (_overlay.value as? Overlay.Popup)?.below ?: Overlay.None
    }

    /**
     * A Home press with something open on top only closes it; otherwise it is the session's rule
     * (Home opens search, anywhere else returns Home). Without this, search would open under the overlay.
     */
    fun onHomePressed(session: LauncherSession) {
        if (_overlay.value != Overlay.None) dismissOverlay() else session.onHomePressed()
    }

    private var lastScreen: Screen? = null

    /** Overlays render above every screen, so one must never outlive a change of screen. */
    fun screenChanged(screen: Screen) {
        if (screen != lastScreen) {
            lastScreen = screen
            dismissOverlay()
        }
    }

    private var lastResetToken: Int? = null

    /** A screen lock discards everything transient: overlays, the message and a pending editor. Seen once per token. */
    fun resetTokenChanged(token: Int) {
        val previous = lastResetToken
        lastResetToken = token
        if (previous != null && previous != token) {
            _overlay.value = Overlay.None
            _message.value = null
            _editorTarget.value = null
            settingsNav = null
            settingsStart = SettingsRoute.Root
            listVisit = 0
        }
    }

    fun showMessage(message: ShellMessage) {
        _message.value = message
        _messageSerial.value += 1
    }

    fun dismissMessage() {
        _message.value = null
    }

    companion object {
        /** Home gestures run only on Home with nothing on top, so a swipe never reaches a covered screen. */
        fun gesturesEnabled(screen: Screen, overlay: Overlay): Boolean =
            screen == Screen.Home && overlay == Overlay.None
    }
}
