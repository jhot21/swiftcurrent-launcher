package me.jhot.swiftcurrent.shell

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.shortcuts.PendingPin
import me.jhot.swiftcurrent.shortcuts.PinRequestSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

private class FakePin(override val label: String, private val result: Boolean = true) : PendingPin {
    val accepts = AtomicInteger()
    override fun icon(densityDpi: Int): Drawable? = null
    override fun accept(): Boolean { accepts.incrementAndGet(); return result }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class PinShortcutActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @After
    fun reset() { PinShortcutActivity.sourceOverride = null }

    private fun launch(pin: PendingPin?): ActivityScenario<PinShortcutActivity> {
        PinShortcutActivity.sourceOverride = PinRequestSource { pin }
        return ActivityScenario.launch(Intent(LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT).setClassName(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(), PinShortcutActivity::class.java.name))
    }

    private fun res(id: Int) = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    /** The activity is finishing or already gone; Robolectric does not always walk a resumed activity on to DESTROYED. */
    private fun awaitFinish(scenario: ActivityScenario<PinShortcutActivity>) = compose.waitUntil(5_000) {
        scenario.state == Lifecycle.State.DESTROYED || runCatching {
            var finishing = false
            scenario.onActivity { finishing = it.isFinishing }
            finishing
        }.getOrDefault(true)
    }

    private fun awaitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun a_request_that_throws_while_being_read_finishes_without_a_crash() {
        PinShortcutActivity.sourceOverride = PinRequestSource { throw IllegalStateException("the requester's binder died") }
        val scenario = ActivityScenario.launch<PinShortcutActivity>(
            Intent(LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT).setClassName(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(), PinShortcutActivity::class.java.name),
        )
        awaitFinish(scenario)
    }

    @Test
    fun a_missing_or_invalid_request_finishes_without_a_dialog() {
        val scenario = launch(null)
        awaitFinish(scenario)
    }

    @Test
    fun add_accepts_the_request_once_and_finishes() {
        val pin = FakePin("Chat")
        val scenario = launch(pin)
        awaitText("Chat")
        compose.onNodeWithText(res(R.string.pin_add)).performClick()
        awaitFinish(scenario)
        assertEquals(1, pin.accepts.get())
    }

    @Test
    fun cancel_finishes_without_accepting() {
        val pin = FakePin("Chat")
        val scenario = launch(pin)
        compose.onNodeWithText(res(R.string.pin_cancel)).performClick()
        awaitFinish(scenario)
        assertEquals(0, pin.accepts.get())
    }

    @Test
    fun two_quick_taps_on_add_accept_once() {
        val pin = FakePin("Chat")
        val scenario = launch(pin)
        compose.onNodeWithText(res(R.string.pin_add)).performClick()
        runCatching { compose.onNodeWithText(res(R.string.pin_add)).performClick() }
        awaitFinish(scenario)
        assertEquals(1, pin.accepts.get())
    }

    @Test
    fun a_refused_accept_still_finishes() {
        val pin = FakePin("Chat", result = false)
        val scenario = launch(pin)
        compose.onNodeWithText(res(R.string.pin_add)).performClick()
        awaitFinish(scenario)
        assertEquals(1, pin.accepts.get())
    }

    @Test
    fun recreating_the_activity_brings_the_dialog_back() {
        val pin = FakePin("Chat")
        val scenario = launch(pin)
        scenario.recreate()
        awaitText("Chat")
        assertEquals(0, pin.accepts.get())
        assertTrue(compose.onAllNodesWithText(res(R.string.pin_add)).fetchSemanticsNodes().isNotEmpty())
    }
}
