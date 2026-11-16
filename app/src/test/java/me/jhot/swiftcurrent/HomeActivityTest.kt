package me.jhot.swiftcurrent

import android.content.Intent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.shell.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeActivityTest {
    // Robolectric has no direct getter for decorFitsSystemWindows. enableEdgeToEdge also applies its
    // automatic bar style, which shows up in the decor view's system UI flags (0 without the call).
    @Test
    fun home_activity_is_edge_to_edge() {
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        val flags = activity.window.decorView.systemUiVisibility
        // Light-icon (dark) style on every theme: the default style would set these flags in Robolectric's light theme.
        assertEquals(0, flags and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR))
    }

    private val homeIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    // Android pauses a singleTask activity before it delivers onNewIntent, so a Home press on a
    // visible launcher arrives paused (STARTED), then the activity resumes.
    @Test
    fun home_intent_on_a_visible_launcher_opens_search() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val session = (controller.get().application as SwiftcurrentApp).session
        session.navigate(Screen.Home)
        controller.pause()
        controller.newIntent(homeIntent)
        controller.resume()
        assertEquals(Screen.Search, session.screen.value)
    }

    @Test
    fun home_intent_while_stopped_goes_home_not_search() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val session = (controller.get().application as SwiftcurrentApp).session
        session.navigate(Screen.Home)
        // Returning from another app: the activity is stopped when the intent arrives.
        controller.pause().stop()
        controller.newIntent(homeIntent)
        assertEquals(Screen.Home, session.screen.value)
    }

    @Test
    fun a_non_home_intent_leaves_the_screen_alone() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val session = (controller.get().application as SwiftcurrentApp).session
        controller.newIntent(Intent(Intent.ACTION_MAIN))
        assertEquals(Screen.Home, session.screen.value)
    }

    @Test
    fun home_press_with_a_sheet_open_only_closes_the_sheet() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val app = controller.get().application as SwiftcurrentApp
        app.session.navigate(Screen.Home)
        app.shellState.openActionSheet(Entry("a", Target.App("pkg.a/.Main")))
        controller.pause()
        controller.newIntent(homeIntent)
        controller.resume()
        assertEquals(Overlay.None, app.shellState.overlay.value)
        assertEquals(Screen.Home, app.session.screen.value)
    }
}
