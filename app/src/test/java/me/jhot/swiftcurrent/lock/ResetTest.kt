package me.jhot.swiftcurrent.lock

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ResetTest {
    @Test
    fun screen_off_resets_to_home_and_discards_scroll() {
        val session = LauncherSession()
        session.navigate(Screen.Settings)
        val before = session.resetToken.value
        session.onScreenOff()
        assertEquals(Screen.Home, session.screen.value)
        // The shell keys list state on the token, so a new value drops scroll, sheets and pop-ups.
        assertEquals(before + 1, session.resetToken.value)
    }

    @Test
    fun reset_happens_for_any_screen_off_not_only_the_double_tap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = LauncherSession()
        session.navigate(Screen.AppList)
        val receiver = ScreenResetReceiver(session)
        context.registerReceiver(receiver, ScreenResetReceiver.filter(), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(Screen.Home, session.screen.value)
        assertEquals(1, session.resetToken.value)
        context.unregisterReceiver(receiver)
    }

    @Test
    fun other_broadcasts_do_not_reset() {
        val session = LauncherSession()
        session.navigate(Screen.AppList)
        ScreenResetReceiver(session).onReceive(ApplicationProvider.getApplicationContext(), Intent(Intent.ACTION_SCREEN_ON))
        assertEquals(Screen.AppList, session.screen.value)
    }

    @Test
    fun home_pressed_on_home_opens_search() {
        val session = LauncherSession()
        session.onHomePressed()
        assertEquals(Screen.Search, session.screen.value)
    }

    @Test
    fun home_pressed_elsewhere_goes_home() {
        val session = LauncherSession()
        for (from in listOf(Screen.AppList, Screen.Search, Screen.Settings)) {
            session.navigate(from)
            session.onHomePressed()
            assertEquals(Screen.Home, session.screen.value)
        }
    }
}
