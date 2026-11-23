package me.jhot.swiftcurrent.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.shortcuts.Creator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class AppShortcutsPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val alpha = "pkg.alpha.maps"
    private val creator = Creator("$alpha/.Friend", "Friend's location", "Alpha Maps")

    private fun group(count: Int = 2, creators: List<Creator> = listOf(creator), bobOn: Boolean = true) = AppShortcutGroup(
        packageName = alpha, appLabel = "Alpha Maps",
        fromApp = (1..count).map { SystemShortcutRow("$alpha/s$it", "Route $it", true) },
        userMade = listOf(UserShortcutRow("u1", "user/u1", "Friend Bob", bobOn)),
        creators = creators,
    )

    private val toggled = mutableListOf<Pair<String, Boolean>>()
    private val toggledAll = mutableListOf<Pair<String, Boolean>>()
    private val deleted = mutableListOf<String>()
    private val created = mutableListOf<Creator>()
    private var granted = 0
    private var grantedShortcuts = 0
    private val actions = AppShortcutsActions(
        onToggle = { k, on -> toggled += k to on },
        onToggleAll = { p, on -> toggledAll += p to on },
        onDelete = { deleted += it.id },
        onCreate = { created += it },
        onGrantContacts = { granted++ },
        onGrantShortcuts = { grantedShortcuts++ },
    )

    private fun show(groups: List<AppShortcutGroup>, contactsGranted: Boolean = true, shortcutsGranted: Boolean = true, startAt: String? = null, onBack: () -> Unit = {}) {
        compose.setContent {
            var open by remember { mutableStateOf(startAt) }
            AppShortcutsPage(groups, AppShortcutGroups.sources(contactsGranted, shortcutsGranted), open, { open = it }, actions, onBack)
        }
    }

    @Test
    fun the_list_counts_shortcuts_per_app_and_opens_an_app() {
        show(listOf(group()))
        compose.onNodeWithText(resources.getQuantityString(R.plurals.app_shortcuts_count_and_make, 3, 3)).assertExists()
        compose.onNodeWithText("Alpha Maps").performClick()
        compose.onNodeWithTag("app-shortcuts-app").assertExists()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_made)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_from, "Alpha Maps")).assertExists()
    }

    @Test
    fun an_app_with_only_creators_says_make_your_own() {
        show(listOf(AppShortcutGroup(alpha, "Alpha Maps", emptyList(), emptyList(), listOf(creator))))
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_make_own)).assertExists()
    }

    @Test
    fun the_contacts_row_carries_the_permission_badge_only_while_it_is_missing() {
        show(listOf(group()), contactsGranted = false)
        compose.onNodeWithTag("permission-badge-contacts", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_source_contacts)).performClick()
        assertEquals(1, granted)
    }

    @Test
    fun the_shortcuts_row_carries_the_badge_when_the_app_is_not_the_default_home() {
        show(listOf(group()), shortcutsGranted = false)
        compose.onNodeWithTag("permission-badge-shortcuts", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("permission-badge-contacts", useUnmergedTree = true).assertCountEquals(0)
        // The title and the row share a word, so tap through the badge, which sits in the row.
        compose.onNodeWithTag("permission-badge-shortcuts", useUnmergedTree = true).performClick()
        assertEquals(1, grantedShortcuts)
    }

    @Test
    fun with_every_permission_granted_there_is_no_search_sources_section() {
        show(listOf(group()))
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_sources)).assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_source_apps)).assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_source_contacts)).assertDoesNotExist()
    }

    @Test
    fun only_a_source_that_needs_a_permission_is_listed() {
        show(listOf(group()), contactsGranted = false)
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_sources)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_source_contacts)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_source_apps)).assertDoesNotExist()
        // Another row carries the word "Shortcuts" too, so a source row is told apart by its badge.
        compose.onAllNodesWithTag("permission-badge-shortcuts", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun no_badge_when_contacts_is_granted() {
        show(listOf(group()), contactsGranted = true)
        compose.onAllNodesWithTag("permission-badge-contacts", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun toggles_delete_toggle_all_and_creators_call_back_with_the_right_keys() {
        show(listOf(group()), startAt = alpha)
        compose.onNodeWithTag("shortcut-toggle-$alpha/s1").assertIsOn().performClick()
        compose.onNodeWithTag("shortcut-toggle-user/u1").performClick()
        assertEquals(listOf("$alpha/s1" to false, "user/u1" to false), toggled)
        compose.onNodeWithTag("shortcut-delete-u1").performClick()
        assertEquals(listOf("u1"), deleted)
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_toggle_all_off)).performClick()
        assertEquals(listOf(alpha to false), toggledAll)
        compose.onNodeWithText("Friend's location").performClick()
        assertEquals(listOf(creator), created)
    }

    @Test
    fun a_disabled_row_shows_off_and_toggle_all_then_offers_to_turn_on() {
        show(listOf(group(bobOn = false)), startAt = alpha)
        compose.onNodeWithTag("shortcut-toggle-user/u1").assertIsOff()
        compose.onNodeWithText(resources.getString(R.string.app_shortcuts_toggle_all_on)).performClick()
        assertEquals(listOf(alpha to true), toggledAll)
    }

    @Test
    fun an_app_opens_in_a_sheet_over_the_list_not_a_full_page() {
        show(listOf(group()))
        compose.onNodeWithText("Alpha Maps").performClick()
        compose.onNodeWithTag("app-shortcuts-app").assertExists()
        // The list is still there under the sheet.
        compose.onNodeWithTag("app-shortcuts-list").assertExists()
    }

    @Test
    fun back_on_an_app_sheet_closes_it_and_back_on_the_list_leaves() {
        var left = 0
        show(listOf(group()), startAt = alpha, onBack = { left++ })
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onAllNodesWithTag("app-shortcuts-app").assertCountEquals(0)
        compose.onNodeWithTag("app-shortcuts-list").assertExists()
        assertEquals(0, left)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, left)
    }

    @Test
    fun dragging_the_sheet_title_down_closes_it() {
        show(listOf(group()), startAt = alpha)
        compose.onAllNodesWithText("Alpha Maps")[1].performTouchInput { swipe(Offset(centerX, 5f), Offset(centerX, 600f)) }
        compose.waitForIdle()
        compose.onAllNodesWithTag("app-shortcuts-app").assertCountEquals(0)
    }

    @Test
    fun tapping_outside_the_sheet_closes_it() {
        show(listOf(group()), startAt = alpha)
        compose.onNodeWithTag("app-shortcuts-app").performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()
        compose.onAllNodesWithTag("app-shortcuts-app").assertCountEquals(0)
    }

    @Test
    fun closing_the_sheet_leaves_the_list_where_it_was_scrolled() {
        val many = (1..40).map { n ->
            AppShortcutGroup("pkg.app$n", "App %02d".format(n), listOf(SystemShortcutRow("pkg.app$n/s", "Route", true)), emptyList(), emptyList())
        }
        val state = androidx.compose.foundation.lazy.LazyListState()
        compose.setContent {
            var open by remember { mutableStateOf<String?>(null) }
            AppShortcutsPage(many, AppShortcutGroups.sources(true, true), open, { open = it }, actions, {}, listState = state)
        }
        compose.runOnUiThread { kotlinx.coroutines.runBlocking { state.scrollToItem(30) } }
        compose.waitForIdle()
        val index = state.firstVisibleItemIndex
        val offset = state.firstVisibleItemScrollOffset
        assertTrue("the list really scrolled, at $index", index >= 25)
        val visible = (1..40).map { "App %02d".format(it) }.first { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(visible).performClick()
        compose.onNodeWithTag("app-shortcuts-app").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onAllNodesWithTag("app-shortcuts-app").assertCountEquals(0)
        assertEquals(index, state.firstVisibleItemIndex)
        assertEquals(offset, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun app_shortcuts_sheet_screenshot() {
        show(listOf(group()), startAt = alpha)
        compose.onRoot().captureRoboImage("src/test/snapshots/app_shortcuts_sheet.png")
    }
}
