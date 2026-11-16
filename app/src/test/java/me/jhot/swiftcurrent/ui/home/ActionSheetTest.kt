package me.jhot.swiftcurrent.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ActionSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun res(id: Int) = compose.activity.getString(id)

    private fun show(entry: Entry, calls: MutableList<String> = mutableListOf()) {
        val cb = ActionSheetCallbacks(
            onEditFavorites = { calls += "edit" }, onAppInfo = { calls += "info" }, onHide = { calls += "hide" },
            onRename = { calls += "rename" },
        )
        compose.setContent { ActionSheet(entry, onDismiss = {}, callbacks = cb) }
    }

    @Test
    fun delete_row_is_absent_without_a_callback() {
        show(Entry("s", Target.Shortcut("pkg/id"), customLabel = "S"))
        compose.onNodeWithTag("sheet-delete").assertDoesNotExist()
    }

    @Test
    fun delete_row_reports_its_tap() {
        val calls = mutableListOf<String>()
        val cb = ActionSheetCallbacks(onDelete = { calls += "delete" })
        compose.setContent { ActionSheet(Entry("s", Target.Shortcut("pkg/id"), customLabel = "S"), onDismiss = {}, callbacks = cb) }
        compose.onNodeWithText(res(R.string.action_sheet_delete_shortcut)).performClick()
        assertEquals(listOf("delete"), calls)
    }

    @Test
    fun an_app_offers_edit_favorites_app_info_and_hide_and_each_row_reports_its_tap() {
        val calls = mutableListOf<String>()
        show(Entry("h", Target.App("pkg.h/.Main"), customLabel = "Host"), calls)
        compose.onNodeWithText("Host").assertIsDisplayed()
        listOf(R.string.action_sheet_edit_favorites, R.string.action_sheet_app_info, R.string.action_sheet_hide).forEach {
            compose.onNodeWithText(res(it)).performClick()
        }
        assertEquals(listOf("edit", "info", "hide"), calls)
        // Each row carries an icon chip glyph.
        listOf("Edit", "Rename", "Info", "Hide").forEach { compose.onAllNodesWithTag("action-glyph-$it", useUnmergedTree = true).assertCountEquals(1) }
    }

    private fun assertRenameRow(entry: Entry) {
        val calls = mutableListOf<String>()
        show(entry, calls)
        compose.onNodeWithText(res(R.string.action_sheet_rename)).assertIsDisplayed().performClick()
        assertEquals(listOf("rename"), calls)
        compose.onAllNodesWithTag("action-glyph-Rename", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun the_sheet_has_a_drag_handle_and_dragging_the_title_down_closes_it() {
        var dismissed = 0
        compose.setContent { ActionSheet(Entry("h", Target.App("pkg.h/.Main"), customLabel = "Host"), onDismiss = { dismissed++ }) }
        compose.onNodeWithTag("sheet-handle", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("sheet-handle", useUnmergedTree = true).performTouchInput { swipe(Offset(centerX, 0f), Offset(centerX, 500f)) }
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun an_app_offers_rename() = assertRenameRow(Entry("h", Target.App("pkg.h/.Main")))

    @Test
    fun a_shortcut_offers_rename() = assertRenameRow(Entry("s", Target.Shortcut("pkg.s/one")))

    @Test
    fun a_folder_offers_rename() = assertRenameRow(Entry("f", Target.Folder))

    @Test
    fun there_is_no_uninstall_row() {
        show(Entry("h", Target.App("pkg.h/.Main"), customLabel = "Host"))
        compose.onAllNodesWithText("Uninstall").assertCountEquals(0)
    }

    @Test
    fun a_shortcut_has_no_app_info_or_hide() {
        val calls = mutableListOf<String>()
        show(Entry("s", Target.Shortcut("pkg.s/one"), customLabel = "Shortcut one"), calls)
        compose.onNodeWithText(res(R.string.action_sheet_edit_favorites)).performClick()
        compose.onAllNodesWithText(res(R.string.action_sheet_app_info)).assertCountEquals(0)
        compose.onAllNodesWithText(res(R.string.action_sheet_hide)).assertCountEquals(0)
        assertEquals(listOf("edit"), calls)
    }

    @Test
    fun a_folder_has_no_app_info_or_hide() {
        show(Entry("f", Target.Folder, customLabel = "Folder"))
        compose.onNodeWithText(res(R.string.action_sheet_edit_favorites)).assertIsDisplayed()
        compose.onAllNodesWithText(res(R.string.action_sheet_app_info)).assertCountEquals(0)
        compose.onAllNodesWithText(res(R.string.action_sheet_hide)).assertCountEquals(0)
    }

    @Test
    fun an_entry_with_no_label_is_named_by_its_kind_never_by_its_raw_id() {
        show(Entry("raw-id-123", Target.App("pkg.h/.Main")))
        compose.onNodeWithText(res(R.string.label_unknown_app)).assertIsDisplayed()
        compose.onAllNodesWithText("raw-id-123").assertCountEquals(0)
    }
}
