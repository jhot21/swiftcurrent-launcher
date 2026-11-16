package me.jhot.swiftcurrent.shell

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.lock.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SetupShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val apps = listOf("Alpha", "Bravo").map { AppInfo("pkg.${it.lowercase()}/.Main", it, "pkg.${it.lowercase()}") }
    private val favorites = listOf(Entry("fav-alpha", Target.App("pkg.alpha/.Main")))
    private val perms = FakePermissions()

    // Held across the recreation like SwiftcurrentApp's: the recreated composition gets the same
    // state and data, never a private copy that could disagree with what a click already changed.
    private lateinit var state: ShellState
    private lateinit var model: ShellData

    private fun res(id: Int, vararg a: Any) = compose.activity.getString(id, *a)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitForTag(tag: String) = compose.waitUntil(5_000) { exists(tag) }
    private fun waitForNoTag(tag: String) = compose.waitUntil(5_000) { !exists(tag) }

    private fun start(
        data: LauncherData = LauncherData(favorites = favorites),
        seen: Boolean = false,
        env: FakeSetupEnv.() -> Unit = {},
    ): FakeDependencies {
        val deps = FakeDependencies(data, apps, setupSeen = seen)
        deps.fakeSetup.env()
        state = ShellState()
        model = ShellData(deps, Dispatchers.IO)
        compose.setContent { LauncherShell(deps, state = state, model = model, permissions = { onResult -> perms.also { it.onResult = onResult } }) }
        compose.waitUntil(5_000) { exists("clock") }
        return deps
    }

    // Absence cannot be waited for, so give the first-run check (it reads the device off the main thread) time to run.
    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(400)
        compose.waitForIdle()
    }

    // The page starts with every step missing and reads the real state off the main thread, so its
    // button says "Skip" first and "Done" only once that read lands; clicking before then misses it.
    private fun clickDone() {
        val done = res(R.string.setup_finish)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(done).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(done).performClick()
    }

    private fun startSeenSetup(): FakeDependencies {
        val deps = start(seen = false)
        waitForTag("setup-page")
        return deps
    }

    @Test
    fun first_run_shows_setup_when_not_seen_and_a_step_is_missing() {
        val deps = start()
        waitForTag("setup-page")
        assertEquals(Screen.Setup, deps.session.screen.value)
        compose.onNodeWithText(res(R.string.setup_progress, 0, 4)).assertExists()
    }

    @Test
    fun setup_is_not_shown_when_already_seen() {
        val deps = start(seen = true)
        settle()
        assertFalse(exists("setup-page"))
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun setup_is_not_shown_when_every_step_is_done() {
        val deps = start { home = true; contacts = true; calendar = true; lock = true }
        settle()
        assertFalse(exists("setup-page"))
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun skipping_marks_setup_seen_and_goes_home() {
        val deps = startSeenSetup()
        compose.onNodeWithText(res(R.string.setup_skip)).performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.setupSeen }
        waitForNoTag("setup-page")
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun back_on_the_first_run_counts_as_skipping() {
        val deps = startSeenSetup()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.setupSeen }
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun the_prompt_comes_once_per_process_not_after_every_screen_off() {
        val deps = startSeenSetup()
        compose.runOnUiThread { deps.session.navigate(Screen.Home); deps.session.onScreenOff() }
        settle()
        assertFalse(exists("setup-page"))
    }

    @Test
    fun default_launcher_step_opens_the_home_settings_and_never_requests_the_role() {
        val deps = startSeenSetup()
        compose.onNodeWithTag("setup-action-DefaultLauncher").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(Settings.ACTION_HOME_SETTINGS, deps.started.last().action)
        assertTrue(perms.requests.isEmpty())
    }

    @Test
    fun contacts_step_requests_the_permission() {
        startSeenSetup()
        compose.onNodeWithTag("setup-action-Contacts").performClick()
        assertEquals(listOf(Manifest.permission.READ_CONTACTS), perms.requests.toList())
    }

    @Test
    fun calendar_step_requests_the_permission() {
        startSeenSetup()
        compose.onNodeWithTag("setup-action-Calendar").performClick()
        assertEquals(listOf(Manifest.permission.READ_CALENDAR), perms.requests.toList())
    }

    @Test
    fun a_first_denial_asks_again_but_a_permanent_one_opens_app_details() {
        val deps = startSeenSetup()
        // First denial: the system would still explain itself, so the next tap asks again.
        perms.rationale += Manifest.permission.READ_CONTACTS
        compose.onNodeWithTag("setup-action-Contacts").performClick()
        compose.runOnUiThread { perms.deliver(Manifest.permission.READ_CONTACTS, false) }
        compose.waitForIdle()
        compose.onNodeWithTag("setup-action-Contacts").performClick()
        assertEquals(2, perms.requests.size)
        assertTrue(deps.started.isEmpty())
    }

    @Test
    fun permanently_denied_permission_opens_app_details() {
        val deps = startSeenSetup()
        compose.onNodeWithTag("setup-action-Contacts").performClick()
        // Denied and the system no longer explains: it will not show the dialog again.
        compose.runOnUiThread { perms.deliver(Manifest.permission.READ_CONTACTS, false) }
        compose.waitForIdle()
        compose.onNodeWithTag("setup-action-Contacts").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(1, perms.requests.size)
        val intent = deps.started.last()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${compose.activity.packageName}", intent.data.toString())
    }

    @Test
    fun accessibility_step_opens_the_accessibility_settings() {
        val deps = startSeenSetup()
        compose.onNodeWithTag("setup-action-Accessibility").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, deps.started.last().action)
    }

    @Test
    fun a_granted_result_marks_the_step_done() {
        val deps = startSeenSetup()
        compose.onNodeWithTag("setup-action-Contacts").performClick()
        deps.fakeSetup.contacts = true
        compose.runOnUiThread { perms.deliver(Manifest.permission.READ_CONTACTS, true) }
        waitForTag("setup-done-Contacts")
        compose.onNodeWithText(res(R.string.setup_progress, 1, 4)).assertExists()
    }

    @Test
    fun step_state_is_re_read_when_returning_to_the_app() {
        val deps = startSeenSetup()
        assertFalse(exists("setup-done-Calendar"))
        // Granted in system settings, then back to the launcher.
        deps.fakeSetup.calendar = true
        deps.fakeSetup.home = true
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForTag("setup-done-Calendar")
        waitForTag("setup-done-DefaultLauncher")
        compose.onNodeWithText(res(R.string.setup_progress, 2, 4)).assertExists()
    }

    @Test
    fun advanced_has_a_setup_row_that_reopens_setup_without_changing_setupSeen() {
        val deps = start(seen = false) { home = true; contacts = true; calendar = true; lock = true }
        settle()
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText(res(R.string.settings_advanced)).performClick()
        waitForTag("settings-advanced")
        compose.onNodeWithText(res(R.string.settings_advanced_setup)).performClick()
        waitForTag("setup-page")
        assertEquals(Screen.Setup, deps.session.screen.value)
        clickDone()
        // Back where it was opened from, and the stored flag was not touched.
        waitForTag("settings-advanced")
        assertEquals(Screen.Settings, deps.session.screen.value)
        assertFalse(deps.fakeStore.data.settings.setupSeen)
    }

    @Test
    fun empty_home_shows_a_hint_that_opens_the_editor() {
        val deps = start(data = LauncherData(), seen = true)
        waitForTag("empty-favorites-hint")
        compose.onNodeWithText(res(R.string.home_add_favorites)).assertExists()
        compose.onNodeWithTag("empty-favorites-hint").performClick()
        compose.waitUntil(5_000) { deps.session.screen.value == Screen.Editor }
    }

    @Test
    fun the_hint_is_absent_when_favorites_exist() {
        start(seen = true)
        waitForTag("favorite-fav-alpha")
        assertFalse(exists("empty-favorites-hint"))
    }

    // The activity is recreated by a dark-mode, font or language change; the deps (and session) live on.
    private fun recreateWith(deps: FakeDependencies) {
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { a ->
            a.setContent { LauncherShell(deps, state = state, model = model, permissions = { onResult -> perms.also { it.onResult = onResult } }) }
        }
        waitForTag("setup-page")
    }

    @Test
    fun skipping_first_run_setup_after_the_activity_is_recreated_still_marks_seen_and_goes_home() {
        val deps = startSeenSetup()
        recreateWith(deps)
        compose.onNodeWithText(res(R.string.setup_skip)).performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.setupSeen }
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun skipping_setup_opened_from_advanced_after_recreation_returns_to_settings_without_marking_seen() {
        val deps = start(seen = false) { home = true; contacts = true; calendar = true; lock = true }
        settle()
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText(res(R.string.settings_advanced)).performClick()
        waitForTag("settings-advanced")
        compose.onNodeWithText(res(R.string.settings_advanced_setup)).performClick()
        waitForTag("setup-page")
        recreateWith(deps)
        clickDone()
        waitForTag("settings-advanced")
        assertEquals(Screen.Settings, deps.session.screen.value)
        assertFalse(deps.fakeStore.data.settings.setupSeen)
    }

    /**
     * The ordering the recreation flake depended on: a click and the composition that renders its
     * result must read the same page stack. The shell holds it for the process (as SwiftcurrentApp
     * does); a recreated composition that built its own would open Settings at Root however far the
     * visit had got.
     */
    @Test
    fun a_recreated_composition_reads_the_settings_page_stack_the_shell_holds() {
        val deps = start(seen = false) { home = true; contacts = true; calendar = true; lock = true }
        settle()
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText(res(R.string.settings_advanced)).performClick()
        waitForTag("settings-advanced")
        compose.onNodeWithText(res(R.string.settings_advanced_setup)).performClick()
        waitForTag("setup-page")
        recreateWith(deps)
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-advanced")
    }
}
