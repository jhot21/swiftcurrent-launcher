package me.jhot.swiftcurrent.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class AnimatedRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private var items by mutableStateOf(listOf("a", "b"))
    private val leavingSeen = mutableMapOf<String, Boolean>()

    // Paused before the content shows, so the first frame is really the first frame.
    private fun show(initial: List<String>) {
        items = initial
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column {
                AnimatedRows(items, key = { it }) { k, leaving ->
                    leavingSeen[k] = leaving
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("row-$k"))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun setItems(vararg keys: String) {
        // A write made under a paused clock reaches the recomposer only once the snapshot is applied.
        compose.runOnUiThread {
            items = keys.toList()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun height(k: String) = compose.onNodeWithTag("row-$k").getBoundsInRoot().let { (it.bottom - it.top).value }

    private fun count(k: String) = compose.onAllNodesWithTag("row-$k").fetchSemanticsNodes().size

    @Test
    fun rows_at_first_composition_are_full_height_on_the_first_frame() {
        show(listOf("a", "b"))
        assertEquals(48f, height("a"), 0.5f)
        assertEquals(48f, height("b"), 0.5f)
    }

    @Test
    fun a_new_row_grows_from_nothing_to_full_height_over_HOME_ROW_MS() {
        show(listOf("a", "b"))
        setItems("a", "b", "c")
        compose.mainClock.advanceTimeBy(HOME_ROW_MS / 2L)
        val mid = height("c")
        assertTrue("half way, got $mid", mid > 0f && mid < 48f)
        compose.mainClock.advanceTimeBy(HOME_ROW_MS + 64L)
        assertEquals(48f, height("c"), 0.5f)
    }

    @Test
    fun a_removed_row_stays_composed_while_fading_then_goes() {
        show(listOf("a", "b"))
        setItems("a")
        compose.mainClock.advanceTimeBy(HOME_ROW_MS / 2L)
        assertEquals(1, count("b"))
        compose.mainClock.advanceTimeBy(HOME_ROW_MS + 64L)
        compose.onAllNodesWithTag("row-b").assertCountEquals(0)
    }

    @Test
    fun a_leaving_row_is_told_it_is_leaving() {
        show(listOf("a", "b"))
        assertEquals(false, leavingSeen["b"])
        setItems("a")
        compose.mainClock.advanceTimeBy(HOME_ROW_MS / 2L)
        assertEquals(true, leavingSeen["b"])
        assertEquals(false, leavingSeen["a"])
    }

    @Test
    fun a_row_that_returns_mid_fade_appears_once_and_ends_visible() {
        show(listOf("a", "b"))
        setItems("a")
        compose.mainClock.advanceTimeBy(HOME_ROW_MS / 2L)
        setItems("a", "b")
        // Turning round mid-fade restarts the transition from where it was, so allow it more than one duration.
        compose.mainClock.advanceTimeBy(HOME_ROW_MS * 2L + 64L)
        assertEquals(1, count("b"))
        assertEquals(48f, height("b"), 0.5f)
        assertEquals(false, leavingSeen["b"])
    }
}
