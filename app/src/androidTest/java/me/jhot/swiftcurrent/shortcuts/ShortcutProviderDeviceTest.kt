package me.jhot.swiftcurrent.shortcuts

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import me.jhot.swiftcurrent.EmulatorOnly
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShortcutProviderDeviceTest {
    @get:Rule
    val emulatorOnly = EmulatorOnly

    @Test
    fun access_is_granted_once_the_app_is_home() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        // Guarded by the rule above: only ever runs on the emulator.
        instrumentation.uiAutomation
            .executeShellCommand("cmd package set-home-activity ${context.packageName}/.HomeActivity")
            .close()
        val provider = LauncherAppsShortcutProvider(context, ShortcutMessages("gone", "unavailable", "needs home"))
        repeat(20) {
            if (provider.access() == ShortcutAccess.Granted) return@repeat
            Thread.sleep(250)
        }
        assertEquals(ShortcutAccess.Granted, provider.access())
        provider.entries() // must not throw once granted
    }
}
