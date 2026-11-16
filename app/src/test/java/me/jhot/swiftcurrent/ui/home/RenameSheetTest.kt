package me.jhot.swiftcurrent.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Renames
import me.jhot.swiftcurrent.ui.ScreenshotBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class RenameSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<String>()
    private var resets = 0
    private var dismissed = 0

    private fun show(current: String, default: String) {
        compose.setContent {
            RenameSheet(
                currentLabel = current, defaultLabel = default,
                onSave = { saved += it }, onReset = { resets++ }, onDismiss = { dismissed++ },
            )
        }
    }

    @Test
    fun the_field_is_prefilled_with_the_current_label_and_the_title_says_rename() {
        show("Browser", "IronFox")
        compose.onNodeWithTag("rename-field").assertTextEquals("Browser")
        compose.onNodeWithText(compose.activity.getString(R.string.rename_title)).assertIsDisplayed()
    }

    @Test
    fun save_is_disabled_until_the_name_changes_and_saves_what_was_typed() {
        show("Browser", "IronFox")
        compose.onNodeWithTag("rename-save").assertIsNotEnabled()
        compose.onNodeWithTag("rename-field").performTextReplacement("Web")
        compose.onNodeWithTag("rename-save").assertIsEnabled().performClick()
        assertEquals(listOf("Web"), saved)
    }

    @Test
    fun typing_the_same_name_with_extra_spaces_is_not_a_change() {
        show("Browser", "IronFox")
        compose.onNodeWithTag("rename-field").performTextReplacement("  Browser ")
        compose.onNodeWithTag("rename-save").assertIsNotEnabled()
    }

    @Test
    fun the_keyboard_done_action_saves() {
        show("Browser", "IronFox")
        compose.onNodeWithTag("rename-field").performTextReplacement("Web")
        compose.onNodeWithTag("rename-field").performImeAction()
        assertEquals(listOf("Web"), saved)
    }

    @Test
    fun done_with_nothing_changed_saves_nothing() {
        show("Browser", "IronFox")
        compose.onNodeWithTag("rename-field").performImeAction()
        assertEquals(emptyList<String>(), saved)
    }

    @Test
    fun reset_is_offered_only_when_a_custom_label_exists_and_names_the_default() {
        show("Browser", "IronFox")
        compose.onNodeWithText(compose.activity.getString(R.string.rename_reset, "IronFox")).assertIsDisplayed().performClick()
        assertEquals(1, resets)
    }

    @Test
    fun there_is_no_reset_when_the_label_is_already_the_default() {
        show("IronFox", "IronFox")
        compose.onAllNodesWithTag("rename-reset").assertCountEquals(0)
    }

    @Test
    fun a_blank_name_can_be_saved_when_a_custom_label_exists_so_it_resets() {
        show("Browser", "IronFox")
        compose.onNodeWithTag("rename-field").performTextClearance()
        compose.onNodeWithTag("rename-save").assertIsEnabled().performClick()
        assertEquals(listOf(""), saved)
    }

    @Test
    fun the_field_cuts_input_at_the_maximum_length() {
        show("Browser", "IronFox")
        compose.onNodeWithTag("rename-field").performTextReplacement("y".repeat(Renames.MAX_LENGTH + 30))
        compose.onNodeWithTag("rename-field").assertTextEquals("y".repeat(Renames.MAX_LENGTH))
    }

    @Test
    fun a_long_label_does_not_break_the_sheet() {
        val long = "w".repeat(Renames.MAX_LENGTH)
        show(long, "Z".repeat(Renames.MAX_LENGTH))
        compose.onNodeWithTag("rename-save").assertIsDisplayed()
        compose.onNodeWithTag("rename-reset").assertIsDisplayed()
    }

    @Test
    fun back_dismisses() {
        show("Browser", "IronFox")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun the_draft_survives_the_activity_being_recreated() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            RenameSheet(currentLabel = "Browser", defaultLabel = "IronFox", onSave = {}, onReset = {}, onDismiss = {})
        }
        compose.onNodeWithTag("rename-field").performTextReplacement("Half typed")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("rename-field").assertTextEquals("Half typed")
    }

    @Test
    fun rename_sheet() {
        compose.setContent {
            ScreenshotBackdrop { RenameSheet(currentLabel = "Browser", defaultLabel = "IronFox", onSave = {}, onReset = {}, onDismiss = {}, showCursor = false) }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/rename_sheet.png")
    }
}
