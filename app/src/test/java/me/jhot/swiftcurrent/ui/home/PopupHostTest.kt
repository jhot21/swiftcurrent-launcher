package me.jhot.swiftcurrent.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.ui.ScreenshotBackdrop
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
class PopupHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun item(id: String) = PopupRow.Item(Entry(id, Target.App("pkg.$id/.Main"), customLabel = "Item $id"))
    private val shortcuts = { _: String -> listOf(ShortcutRowModel("s1", "Shortcut one")) }

    private fun tops(vararg tags: String) = tags.map { compose.onNodeWithTag(it, useUnmergedTree = true).getBoundsInRoot().top.value }

    @Test
    fun popup_shows_own_shortcuts_row_in_stored_order_and_omits_it_when_hidden() {
        val host = Entry(
            "h", Target.App("pkg.h/.Main"), customLabel = "Host",
            popup = Popup(listOf(item("a"), PopupRow.OwnShortcuts("pkg.h"), item("b"))),
        )
        compose.setContent { PopupHost(host, onDismiss = {}, ownShortcuts = shortcuts) }
        compose.onNodeWithText("Host").assertIsDisplayed()
        val t = tops("popup-row-0", "popup-row-1", "popup-row-2")
        assertTrue("rows must follow stored order: $t", t[0] < t[1] && t[1] < t[2])
        compose.onNodeWithText("Item a").assertIsDisplayed()
        compose.onNodeWithText("Shortcut one").assertIsDisplayed()
    }

    @Test
    fun hidden_own_shortcuts_row_is_omitted() {
        val host = Entry("h", Target.App("pkg.h/.Main"), popup = Popup(listOf(item("a"), item("b"))))
        compose.setContent { PopupHost(host, onDismiss = {}, ownShortcuts = shortcuts) }
        compose.onAllNodesWithText("Shortcut one").assertCountEquals(0)
        compose.onAllNodesWithTag("popup-row-2").assertCountEquals(0)
    }

    @Test
    fun entry_without_a_custom_popup_shows_only_its_own_shortcuts() {
        val host = Entry("h", Target.App("pkg.h/.Main"), customLabel = "Host")
        var asked = ""
        compose.setContent { PopupHost(host, onDismiss = {}, ownShortcuts = { asked = it; shortcuts(it) }) }
        assertEquals("pkg.h", asked)
        compose.onNodeWithText("Shortcut one").assertIsDisplayed()
    }

    @Test
    fun tapping_outside_the_card_dismisses() {
        var dismissed = 0
        compose.setContent { PopupHost(Entry("h", Target.App("pkg.h/.Main")), onDismiss = { dismissed++ }, ownShortcuts = shortcuts) }
        compose.onNodeWithTag("popup-scrim").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun back_press_dismisses_the_popup() {
        var dismissed = 0
        compose.setContent { PopupHost(Entry("h", Target.App("pkg.h/.Main")), onDismiss = { dismissed++ }, ownShortcuts = shortcuts) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun a_popup_with_nothing_to_list_says_so_and_still_offers_edit() {
        var edits = 0
        compose.setContent {
            PopupHost(Entry("h", Target.App("pkg.h/.Main"), customLabel = "Host"), onDismiss = {}, onEdit = { edits++ })
        }
        compose.onNodeWithTag("popup-empty", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("popup-edit", useUnmergedTree = true).performClick()
        assertEquals(1, edits)
    }

    @Test
    fun the_header_sits_above_a_rule_and_the_rows_have_the_dot() {
        val host = Entry("h", Target.App("pkg.h/.Main"), customLabel = "Host", popup = Popup(listOf(item("a"))))
        compose.setContent { PopupHost(host, onDismiss = {}, ownShortcuts = shortcuts, onEdit = {}) }
        val t = tops("popup-header", "popup-row-0")
        assertTrue("header must come first: $t", t[0] < t[1])
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Item a").assertIsDisplayed()
    }

    private val density get() = compose.activity.resources.displayMetrics.density
    private fun anchorAt(topDp: Float) =
        androidx.compose.ui.geometry.Rect(0f, topDp * density, 411f * density, (topDp + 56f) * density)
    private fun hostWith(count: Int) = Entry(
        "h", Target.App("pkg.h/.Main"), customLabel = "Host",
        popup = Popup((1..count).map { item("i$it") }),
    )
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun the_first_line_lines_up_with_the_item_that_was_swiped() {
        compose.setContent { PopupHost(hostWith(2), onDismiss = {}, anchor = anchorAt(500f)) }
        assertEquals(500f, bounds("popup-line-0").top.value, 1f)
        // The sheet reaches the bottom of the screen; it does not float.
        assertEquals(891f, bounds("popup-sheet").bottom.value, 1f)
    }

    @Test
    fun the_sheet_grows_upward_instead_of_hiding_lines_when_the_item_is_near_the_bottom() {
        compose.setContent { PopupHost(hostWith(4), onDismiss = {}, anchor = anchorAt(840f)) }
        val last = bounds("popup-line-3")
        assertTrue("last line must be on screen, bottom=${last.bottom}", last.bottom.value <= 891f)
        assertTrue("the first line moved up from the item", bounds("popup-line-0").top.value < 840f)
        for (n in 0..3) compose.onNodeWithTag("popup-line-$n", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun more_lines_than_fit_take_the_full_height_and_scroll() {
        compose.setContent { PopupHost(hostWith(40), onDismiss = {}, anchor = anchorAt(500f)) }
        val sheet = bounds("popup-sheet")
        assertTrue("sheet should reach near the top, top=${sheet.top}", sheet.top.value <= 60f)
        compose.onNodeWithTag("popup-line-39", useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithTag("popup-line-39", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_sheet_shows_a_drag_handle_above_its_title() {
        compose.setContent { PopupHost(hostWith(2), onDismiss = {}, anchor = anchorAt(500f)) }
        assertTrue(bounds("sheet-handle").bottom.value <= bounds("popup-header").top.value + 1f)
        assertEquals(205.5f, bounds("sheet-handle").let { (it.left + it.right).value / 2f }, 1f)
    }

    @Test
    fun without_an_anchor_the_sheet_sits_at_the_bottom() {
        compose.setContent { PopupHost(hostWith(2), onDismiss = {}) }
        assertEquals(891f, bounds("popup-sheet").bottom.value, 1f)
        assertTrue(bounds("popup-header").top.value > 400f)
    }

    @Test
    fun dragging_the_header_down_dismisses() {
        var dismissed = 0
        compose.setContent { PopupHost(hostWith(2), onDismiss = { dismissed++ }, anchor = anchorAt(500f)) }
        compose.onNodeWithTag("popup-header", useUnmergedTree = true).performTouchInput { swipe(Offset(centerX, 10f), Offset(centerX, 500f)) }
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun the_sheet_follows_the_finger_while_dragging_and_springs_back_when_released_slowly() {
        var dismissed = 0
        compose.setContent { PopupHost(hostWith(2), onDismiss = { dismissed++ }, anchor = anchorAt(500f)) }
        val rest = bounds("popup-sheet").top.value
        val handle = compose.onNodeWithTag("sheet-handle", useUnmergedTree = true)
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f), delayMillis = 1_000)
        }
        compose.waitForIdle()
        val held = bounds("popup-sheet").top.value
        assertTrue("sheet should have moved with the finger: rest=$rest held=$held", held - rest > 30f)
        assertEquals("still open while held", 0, dismissed)
        // Let go after a pause with no speed: a short, slow drag is not a flick.
        handle.performTouchInput { advanceEventTime(500); up() }
        compose.waitForIdle()
        assertEquals(0, dismissed)
        assertEquals("sheet returns to rest", rest, bounds("popup-sheet").top.value, 1f)
    }

    @Test
    fun an_app_line_swiped_right_reports_its_bounds_and_is_not_launched() {
        val swiped = mutableListOf<Pair<String, androidx.compose.ui.geometry.Rect>>()
        val tapped = mutableListOf<String>()
        compose.setContent {
            PopupHost(
                hostWith(2), onDismiss = {}, anchor = anchorAt(500f),
                onEntry = { e, _ -> tapped += e.id }, onEntrySwipe = { e, r -> swiped += e.id to r },
            )
        }
        val line = bounds("popup-line-1")
        compose.onNodeWithTag("popup-line-1", useUnmergedTree = true).performTouchInput { swipe(Offset(100f, centerY), Offset(700f, centerY)) }
        compose.waitForIdle()
        assertEquals(listOf("i2"), swiped.map { it.first })
        assertEquals(line.top.value * density, swiped.single().second.top, 2f)
        assertTrue("a swipe is not a tap", tapped.isEmpty())
        compose.onNodeWithTag("popup-line-0", useUnmergedTree = true).performClick()
        assertEquals(listOf("i1"), tapped)
    }

    @Test
    fun an_own_shortcut_line_cannot_be_swiped_open() {
        val swiped = mutableListOf<String>()
        val host = Entry("h", Target.App("pkg.h/.Main"), customLabel = "Host")
        compose.setContent { PopupHost(host, onDismiss = {}, ownShortcuts = shortcuts, anchor = anchorAt(500f), onEntrySwipe = { e, _ -> swiped += e.id }) }
        compose.onNodeWithTag("popup-line-0", useUnmergedTree = true).performTouchInput { swipe(Offset(100f, centerY), Offset(700f, centerY)) }
        compose.waitForIdle()
        assertTrue(swiped.isEmpty())
    }

    @Test
    fun popup_sheet_screenshot_aligned_with_the_swiped_item() {
        compose.setContent { ScreenshotBackdrop { PopupHost(hostWith(3), onDismiss = {}, onEdit = {}, anchor = anchorAt(420f)) } }
        compose.onRoot().captureRoboImage("src/test/snapshots/popup_sheet.png")
    }

    @Test
    fun popup_sheet_screenshot_two_stacked() {
        compose.setContent {
            ScreenshotBackdrop {
                PopupHost(hostWith(4), onDismiss = {}, onEdit = {}, anchor = anchorAt(300f))
                PopupHost(hostWith(2), onDismiss = {}, anchor = anchorAt(480f))
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/popup_sheet_stacked.png")
    }
}
