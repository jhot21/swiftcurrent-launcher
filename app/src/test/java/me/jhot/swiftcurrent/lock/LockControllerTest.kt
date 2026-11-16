package me.jhot.swiftcurrent.lock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LockControllerTest {
    private class FakeActions(val result: Boolean) : LockActions {
        var calls = 0
        override fun lockScreen(): Boolean { calls++; return result }
    }

    @Test
    fun lock_with_service_disabled_returns_ServiceDisabled_and_does_not_lock() {
        val actions = FakeActions(true)
        var connected: LockActions? = null
        val controller = AccessibilityLockController { connected }
        assertEquals(LockResult.ServiceDisabled, controller.lock())
        assertEquals(0, actions.calls)
        connected = actions
        assertEquals(LockResult.Locked, controller.lock())
        assertEquals(1, actions.calls)
    }

    @Test
    fun a_refused_global_action_is_a_failure_not_a_disabled_service() {
        // The service is connected, so telling the owner to enable it would be wrong.
        val controller = AccessibilityLockController { FakeActions(false) }
        assertEquals(LockResult.Failed, controller.lock())
    }

    @Test
    fun settings_intent_opens_accessibility_settings() {
        assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, accessibilitySettingsIntent().action)
    }

    private class FakeLock(val result: LockResult) : LockController {
        var calls = 0
        override fun lock(): LockResult { calls++; return result }
    }

    @Test
    fun double_tap_locks_reports_and_resets_the_session() {
        val lock = FakeLock(LockResult.Locked)
        val session = LauncherSession()
        session.navigate(Screen.AppList)
        var now = 0L
        val results = mutableListOf<LockResult>()
        val handler = ScrubTapHandler(DoubleTapDetector(300), lock, session, { now }, results::add)
        handler.onScrubTap('A')
        assertEquals(0, lock.calls)
        now = 100
        handler.onScrubTap('B')
        assertEquals(1, lock.calls)
        assertEquals(listOf<LockResult>(LockResult.Locked), results)
        assertEquals(Screen.Home, session.screen.value)
    }

    @Test
    fun disabled_service_reports_and_leaves_the_session_alone() {
        val lock = FakeLock(LockResult.ServiceDisabled)
        val session = LauncherSession()
        session.navigate(Screen.AppList)
        val results = mutableListOf<LockResult>()
        val handler = ScrubTapHandler(DoubleTapDetector(300), lock, session, { 0L }, results::add)
        handler.onScrubTap('A')
        handler.onScrubTap('A')
        assertEquals(listOf<LockResult>(LockResult.ServiceDisabled), results)
        assertEquals(Screen.AppList, session.screen.value)
        assertTrue(session.resetToken.value == 0)
        assertFalse(results.isEmpty())
    }

    @Test
    fun a_failed_lock_reports_and_leaves_the_session_alone() {
        val lock = FakeLock(LockResult.Failed)
        val session = LauncherSession()
        session.navigate(Screen.AppList)
        val results = mutableListOf<LockResult>()
        val handler = ScrubTapHandler(DoubleTapDetector(300), lock, session, { 0L }, results::add)
        handler.onScrubTap('A')
        handler.onScrubTap('A')
        assertEquals(listOf<LockResult>(LockResult.Failed), results)
        assertEquals(Screen.AppList, session.screen.value)
        assertEquals(0, session.resetToken.value)
    }
}
