package me.jhot.swiftcurrent.shell

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellStateTest {
    private val a = Entry("a", Target.App("pkg.a/.Main"))
    private val b = Entry("b", Target.Folder)

    @Test
    fun starts_with_no_overlay_and_no_message() {
        val s = ShellState()
        assertEquals(Overlay.None, s.overlay.value)
        assertEquals(null, s.message.value)
    }

    @Test
    fun opening_an_overlay_replaces_the_previous_one() {
        val s = ShellState()
        s.openPopup(a)
        assertEquals(Overlay.Popup(a), s.overlay.value)
        s.openActionSheet(a)
        assertEquals(Overlay.ActionSheet(a), s.overlay.value)
        s.dismissOverlay()
        assertEquals(Overlay.None, s.overlay.value)
    }

    @Test
    fun a_popup_opened_over_a_popup_stacks_and_closing_returns_to_the_one_beneath() {
        val s = ShellState()
        s.openPopup(a)
        s.openPopup(b)
        assertEquals(Overlay.Popup(b, below = Overlay.Popup(a)), s.overlay.value)
        s.dismissTop()
        assertEquals(Overlay.Popup(a), s.overlay.value)
        s.dismissTop()
        assertEquals(Overlay.None, s.overlay.value)
    }

    @Test
    fun dismissing_the_overlay_closes_a_whole_stack() {
        val s = ShellState()
        s.openPopup(a)
        s.openPopup(b)
        s.dismissOverlay()
        assertEquals(Overlay.None, s.overlay.value)
    }

    @Test
    fun a_popup_does_not_stack_on_a_sheet() {
        val s = ShellState()
        s.openActionSheet(a)
        s.openPopup(b)
        assertEquals(Overlay.Popup(b), s.overlay.value)
        s.openActionSheet(a)
        s.dismissTop()
        assertEquals(Overlay.None, s.overlay.value)
    }

    @Test
    fun gestures_run_only_on_home_with_no_overlay() {
        val s = ShellState()
        fun enabled(screen: Screen) = ShellState.gesturesEnabled(screen, s.overlay.value)
        assertTrue(enabled(Screen.Home))
        for (screen in listOf(Screen.AppList, Screen.Search, Screen.Settings)) assertFalse(enabled(screen))
        s.openPopup(a)
        assertFalse(enabled(Screen.Home))
        s.dismissOverlay()
        s.openActionSheet(a)
        assertFalse(enabled(Screen.Home))
        s.dismissOverlay()
        assertTrue(enabled(Screen.Home))
    }

    @Test
    fun a_message_stays_until_dismissed_and_reposting_bumps_the_serial() {
        val s = ShellState()
        s.showMessage(ShellMessage.LockNeedsService)
        val first = s.messageSerial.value
        assertEquals(ShellMessage.LockNeedsService, s.message.value)
        s.showMessage(ShellMessage.LockNeedsService)
        assertTrue(s.messageSerial.value > first)
        s.dismissMessage()
        assertEquals(null, s.message.value)
    }

    @Test
    fun overlays_and_messages_are_independent() {
        val s = ShellState()
        s.openPopup(a)
        s.showMessage(ShellMessage.LaunchFailed("x"))
        s.dismissOverlay()
        assertEquals(ShellMessage.LaunchFailed("x"), s.message.value)
        s.openPopup(a)
        s.dismissMessage()
        assertEquals(Overlay.Popup(a), s.overlay.value)
    }

    @Test
    fun home_press_with_a_sheet_open_only_closes_the_sheet() {
        val s = ShellState()
        val session = LauncherSession()
        s.openActionSheet(a)
        s.onHomePressed(session)
        assertEquals(Overlay.None, s.overlay.value)
        assertEquals(Screen.Home, session.screen.value)
    }

    @Test
    fun home_press_with_a_popup_open_only_closes_the_popup() {
        val s = ShellState()
        val session = LauncherSession()
        s.openPopup(a)
        s.onHomePressed(session)
        assertEquals(Overlay.None, s.overlay.value)
        assertEquals(Screen.Home, session.screen.value)
    }

    @Test
    fun home_press_with_the_agenda_open_only_closes_it() {
        val s = ShellState()
        val session = LauncherSession()
        s.openAgenda()
        s.onHomePressed(session)
        assertEquals(Overlay.None, s.overlay.value)
        assertEquals(Screen.Home, session.screen.value)
    }

    @Test
    fun home_press_with_nothing_open_still_opens_search_from_home() {
        val s = ShellState()
        val session = LauncherSession()
        s.onHomePressed(session)
        assertEquals(Screen.Search, session.screen.value)
    }

    @Test
    fun an_overlay_never_survives_a_screen_change() {
        val s = ShellState()
        s.screenChanged(Screen.Home)
        s.openActionSheet(a)
        s.screenChanged(Screen.Home)
        assertEquals("the same screen again is not a change", Overlay.ActionSheet(a), s.overlay.value)
        s.screenChanged(Screen.AppList)
        assertEquals(Overlay.None, s.overlay.value)
    }
}
