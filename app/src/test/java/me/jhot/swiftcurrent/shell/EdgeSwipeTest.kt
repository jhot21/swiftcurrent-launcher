package me.jhot.swiftcurrent.shell

import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.ui.applist.EdgeScrub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EdgeSwipeTest {
    private val session = LauncherSession()
    private val scrub = EdgeScrub()
    private val swipe = EdgeSwipe(scrub, session)

    // What Home's detector does: onAppList navigates, then the finger is handed over.
    private fun land(y: Float = 500f) {
        session.navigate(Screen.AppList)
        swipe.down(y)
    }

    @Test
    fun a_lift_without_moving_keeps_the_list_open() {
        land()
        swipe.up(500f, moved = false)
        assertEquals(Screen.AppList, session.screen.value)
        assertFalse(scrub.active)
    }

    @Test
    fun a_lift_after_moving_keeps_the_list_open() {
        land()
        swipe.move(300f, moved = true)
        swipe.up(300f, moved = true)
        assertEquals(Screen.AppList, session.screen.value)
        assertFalse(scrub.active)
    }

    @Test
    fun a_cancel_before_moving_closes_the_list_and_after_moving_keeps_it() {
        land()
        swipe.cancel()
        assertEquals(Screen.Home, session.screen.value)
        land()
        swipe.move(300f, moved = true)
        swipe.cancel()
        assertEquals(Screen.AppList, session.screen.value)
        assertFalse(scrub.active)
    }

    @Test
    fun a_stray_lift_never_navigates_away_from_another_screen() {
        // Something else moved on (a Home press, a reset) while the finger was down.
        land()
        session.navigate(Screen.Search)
        swipe.up(500f, moved = false)
        assertEquals(Screen.Search, session.screen.value)
    }
}
