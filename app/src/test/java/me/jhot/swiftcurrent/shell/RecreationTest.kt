package me.jhot.swiftcurrent.shell

import android.app.Activity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.HomeActivity
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.SwiftcurrentApp
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.ui.settings.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real [HomeActivity] with its real wiring: what survives the activity being recreated or reconfigured. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class RecreationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    // A hang (an animation that never settles) fails the test instead of stalling the gate.
    @get:Rule
    val timeout = WatchdogTimeout(seconds = 120)

    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<SwiftcurrentApp>()

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { exists(tag) }

    // The system starts a launcher with a HOME intent. ActivityScenario's default intent carries the
    // LAUNCHER category instead, which is the app-icon entry and opens Settings.
    private fun homeIntent() = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_HOME)
        .setClass(androidx.test.core.app.ApplicationProvider.getApplicationContext(), HomeActivity::class.java)

    private fun launch(intent: android.content.Intent = homeIntent()): ActivityScenario<HomeActivity> {
        // The first-run check would otherwise open setup over what these tests build.
        app.shellData.setupPrompted = true
        val scenario = ActivityScenario.launch<HomeActivity>(intent)
        waitForTag("clock")
        return scenario
    }

    private fun back(scenario: ActivityScenario<HomeActivity>) =
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun recreating_while_the_editor_is_open_keeps_it_and_back_still_works() {
        val scenario = launch()
        compose.runOnUiThread { app.shellState.openEditor(EditorTarget.Favorites); app.session.navigate(Screen.Editor) }
        waitForTag("editor-header-Selected")
        scenario.recreate()
        waitForTag("editor-header-Selected")
        assertEquals(Screen.Editor, app.session.screen.value)
        back(scenario)
        compose.waitUntil(10_000) { app.session.screen.value == Screen.Home }
        waitForTag("clock")
        scenario.close()
    }

    @Test
    fun the_app_icon_entry_opens_settings_and_a_recreation_keeps_it_there() {
        val icon = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            .setClass(androidx.test.core.app.ApplicationProvider.getApplicationContext(), HomeActivity::class.java)
        val scenario = launch(icon)
        waitForTag("settings-root")
        scenario.recreate()
        waitForTag("settings-root")
        scenario.close()
    }

    @Test
    fun a_home_launch_shows_home_not_settings() {
        val scenario = launch()
        assertTrue(!exists("settings-root"))
        scenario.close()
    }

    @Test
    fun recreating_while_settings_is_on_a_sub_page_stays_on_that_page() {
        val scenario = launch()
        compose.runOnUiThread { app.shellState.openSettings(SettingsRoute.HiddenApps); app.session.navigate(Screen.Settings) }
        waitForTag("settings-hidden")
        scenario.recreate()
        waitForTag("settings-hidden")
        back(scenario)
        waitForTag("settings-apps")
        scenario.close()
    }

    @Test
    fun recreating_does_not_show_a_black_frame_or_run_first_run_again() {
        val scenario = launch()
        val before = app.shellData
        // The shell runs the application's own data holder, not a private copy.
        assertTrue(before.ready.value)
        scenario.recreate()
        // No waiting: the data survived, so the very first composition already shows Home.
        assertTrue(exists("clock"))
        assertSame(before, app.shellData)
        assertTrue(app.shellData.setupPrompted)
        scenario.close()
    }

    @Test
    fun font_scale_and_dark_mode_changes_are_handled_without_recreating_the_activity() {
        val scenario = launch()
        var first: Activity? = null
        scenario.onActivity { first = it }
        RuntimeEnvironment.setFontScale(1.5f)
        scenario.onActivity { assertSame(first, it) }
        RuntimeEnvironment.setQualifiers("+night")
        scenario.onActivity { assertSame(first, it) }
        // Compose followed the change: the activity's density carries the new scale.
        scenario.onActivity { assertEquals(1.5f, it.resources.configuration.fontScale, 0.001f) }
        waitForTag("clock")
        scenario.close()
    }

    private fun scrubTo(c: Char) {
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val letter = compose.onNodeWithTag(me.jhot.swiftcurrent.ui.applist.scrubTag(c), useUnmergedTree = true).getBoundsInRoot()
        val density = androidx.test.core.app.ApplicationProvider.getApplicationContext<SwiftcurrentApp>().resources.displayMetrics.density
        val at = androidx.compose.ui.geometry.Offset(
            ((strip.right - strip.left).value / 2f) * density,
            ((letter.top + letter.bottom).value / 2f - strip.top.value) * density,
        )
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
    }

    @Test
    fun recreating_with_the_rename_sheet_open_for_a_new_folder_keeps_the_sheet() {
        val scenario = launch()
        compose.runOnUiThread { app.shellState.openEditor(EditorTarget.Favorites); app.session.navigate(Screen.Editor) }
        waitForTag("editor-header-Selected")
        scrubTo(me.jhot.swiftcurrent.ui.editor.EditorSections.ADVANCED)
        waitForTag("action-CreateFolder")
        compose.onNodeWithTag("action-CreateFolder").performClick()
        waitForTag("rename-field")
        scenario.recreate()
        // The folder was saved but the data the editor rebuilds from predates it; the sheet must still show.
        waitForTag("editor-header-Selected")
        compose.waitUntil(10_000) { exists("rename-field") }
        compose.onNodeWithTag("rename-field").assertTextEquals(androidx.test.core.app.ApplicationProvider.getApplicationContext<SwiftcurrentApp>().getString(R.string.editor_new_folder))
        scenario.close()
    }

    @Test
    fun an_edit_made_before_a_recreation_is_not_lost_by_the_next_edit() {
        // A folder only in the library, so ticking it onto Home is a real edit to the stored favorites.
        val folder = Entry("fold-library", Target.Folder, popup = Popup(emptyList()))
        val alpha = Entry("fav-alpha", Target.App("pkg.alpha/.Main"))
        val store = app.dependencies.store
        runBlocking { store.update { LauncherData(favorites = listOf(alpha), folders = listOf(folder)) } }
        val scenario = launch()
        try {
            compose.runOnUiThread { app.shellState.openEditor(EditorTarget.Favorites); app.session.navigate(Screen.Editor) }
            waitForTag("editor-header-Selected")
            // Edit A: tick the folder into Selected, and let the store have it before the recreation.
            scrubTo(me.jhot.swiftcurrent.ui.editor.EditorSections.FOLDERS)
            compose.onNodeWithTag("pick-Folders-${folder.id}").performClick()
            compose.waitUntil(10_000) { runBlocking { store.load().favorites.any { it.id == folder.id } } }
            scenario.recreate()
            waitForTag("editor-header-Selected")
            // The rebuilt editor starts from the store, so A is still ticked.
            compose.waitUntil(10_000) { exists("sel-${folder.id}") }
            // Edit B: untick the app that was already a favorite.
            compose.onNodeWithTag("toggle-sel-${alpha.id}").performClick()
            compose.waitUntil(10_000) { runBlocking { store.load().favorites.none { it.id == alpha.id } } }
            back(scenario)
            compose.waitUntil(10_000) { app.session.screen.value == Screen.Home }
            waitForTag("clock")
            // B's write carried A with it instead of writing back the list from before A.
            val saved = runBlocking { store.load() }
            assertTrue(saved.favorites.any { it.id == folder.id })
            assertTrue(saved.favorites.none { it.id == alpha.id })
            assertTrue(saved.folders.any { it.id == folder.id })
        } finally {
            scenario.close()
            // The store is shared by the whole property test class; leave it as this test found it, even when this one failed.
            runBlocking { store.update { LauncherData() } }
        }
    }
}
