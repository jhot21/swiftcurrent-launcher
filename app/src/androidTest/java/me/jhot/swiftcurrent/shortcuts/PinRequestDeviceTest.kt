package me.jhot.swiftcurrent.shortcuts

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import me.jhot.swiftcurrent.EmulatorOnly
import me.jhot.swiftcurrent.HomeActivity
import me.jhot.swiftcurrent.search.LaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The whole path with the real system: request a pin, tap Add in our dialog, see it pinned, then unpin it. */
class PinRequestDeviceTest {
    @get:Rule
    val emulatorOnly = EmulatorOnly

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private fun <T : Any> poll(what: String, seconds: Int = 15, block: () -> T?): T {
        repeat(seconds * 4) {
            block()?.let { return it }
            Thread.sleep(250)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun textNodes(n: AccessibilityNodeInfo?, text: String): List<AccessibilityNodeInfo> =
        if (n == null) emptyList()
        else (if (n.text?.toString() == text) listOf(n) else emptyList()) + (0 until n.childCount).flatMap { textNodes(n.getChild(it), text) }

    /**
     * Taps the on-screen text. Our dialog is a translucent window over the stock launcher, so it is not
     * always the active window (look at every one), and Compose's merged click node ignores the
     * accessibility click action, so a real touch at the text's centre is the honest test.
     */
    private fun tapText(text: String): Boolean {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val node = automation.windows.mapNotNull { it.root }.filter { it.packageName == context.packageName }
            .flatMap { textNodes(it, text) }.firstOrNull() ?: return false
        val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
        val now = android.os.SystemClock.uptimeMillis()
        for ((action, at) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 50)) {
            val event = MotionEvent.obtain(now, at, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        return true
    }

    @Test
    fun a_pin_request_is_accepted_through_the_dialog_and_can_be_unpinned() {
        // Guarded by the rule above: only ever runs on the emulator.
        instrumentation.uiAutomation
            .executeShellCommand("cmd package set-home-activity ${context.packageName}/.HomeActivity")
            .close()
        val provider = LauncherAppsShortcutProvider(context, ShortcutMessages("gone", "unavailable", "needs home"))
        poll("home access") { provider.access().takeIf { it == ShortcutAccess.Granted } }

        val id = "pin-test-${System.currentTimeMillis()}"
        val key = "${context.packageName}/$id"
        val manager = context.getSystemService(ShortcutManager::class.java)
        val info = ShortcutInfo.Builder(context, id)
            .setShortLabel("Pin test")
            .setIntent(Intent(Intent.ACTION_VIEW).setClassName(context, HomeActivity::class.java.name))
            .build()
        try {
            assertTrue("the system must report pinning as supported once we handle it", manager.isRequestPinShortcutSupported)
            assertTrue(manager.requestPinShortcut(info, null))

            poll("the dialog's Add button") { tapText("Add").takeIf { it } }
            val pinned = poll("the shortcut to be listed as pinned") { provider.entries().firstOrNull { it.key == key && it.pinned } }
            assertEquals("Pin test", pinned.label)

            assertEquals(LaunchOutcome.Launched, provider.unpin(key))
            poll("the shortcut to be gone") { true.takeIf { provider.entries().none { it.key == key } } }
        } finally {
            runCatching { provider.unpin(key) }
        }
    }
}
