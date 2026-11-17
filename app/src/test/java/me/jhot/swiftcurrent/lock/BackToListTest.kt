package me.jhot.swiftcurrent.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class BackToListTest {
    private fun session() = LauncherSession().also { it.navigate(Screen.Home) }

    @Test
    fun within_window_returns_to_list() {
        val s = session()
        s.markListLaunch(1_000)
        s.onReturnedWithoutHome(1_000 + LIST_RETURN_WINDOW_MS - 1)
        assertEquals(Screen.AppList, s.screen.value)
    }

    @Test
    fun at_or_after_window_stays_home() {
        val s = session()
        s.markListLaunch(1_000)
        s.onReturnedWithoutHome(1_000 + LIST_RETURN_WINDOW_MS)
        assertEquals(Screen.Home, s.screen.value)
    }

    @Test
    fun consumed_once() {
        val s = session()
        s.markListLaunch(1_000)
        s.onReturnedWithoutHome(2_000)
        s.navigate(Screen.Home)
        s.onReturnedWithoutHome(3_000)
        assertEquals(Screen.Home, s.screen.value)
    }

    @Test
    fun clear_cancels() {
        val s = session()
        s.markListLaunch(1_000)
        s.clearListReturn()
        s.onReturnedWithoutHome(2_000)
        assertEquals(Screen.Home, s.screen.value)
    }

    @Test
    fun screen_off_cancels() {
        val s = session()
        s.markListLaunch(1_000)
        s.onScreenOff()
        s.onReturnedWithoutHome(2_000)
        assertEquals(Screen.Home, s.screen.value)
    }

    @Test
    fun no_mark_leaves_screen_untouched() {
        val s = session()
        s.navigate(Screen.Settings)
        s.onReturnedWithoutHome(2_000)
        assertEquals(Screen.Settings, s.screen.value)
    }
}
