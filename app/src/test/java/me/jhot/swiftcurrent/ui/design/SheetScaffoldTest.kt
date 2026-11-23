package me.jhot.swiftcurrent.ui.design

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SheetScaffoldTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // The sheet is clickable (it swallows scrim taps), which merges everything inside it.
    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)

    private fun text(t: String) = compose.onNodeWithText(t, useUnmergedTree = true)

    /** Delivers a keyboard of [heightDp] to the composition, as the system does when one opens. */
    private fun showKeyboard(heightDp: Int) {
        compose.runOnUiThread {
            val px = (heightDp * compose.activity.resources.displayMetrics.density).roundToInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            fun dispatch(v: View) {
                ViewCompat.dispatchApplyWindowInsets(v, insets)
                if (v is ViewGroup) (0 until v.childCount).forEach { dispatch(v.getChildAt(it)) }
            }
            dispatch(compose.activity.findViewById<ViewGroup>(android.R.id.content))
        }
        compose.waitForIdle()
    }

    private fun keyboardTop(heightDp: Int) = compose.onRoot().getBoundsInRoot().bottom - heightDp.dp

    private fun showForm() = compose.setContent {
        SheetScaffold(title = "Form", onDismiss = {}) {
            repeat(3) { Box(Modifier.fillMaxWidth().height(60.dp).testTag("row-$it")) }
        }
    }

    @Test
    fun a_tall_keyboard_lifts_the_whole_sheet_so_a_short_form_stays_in_view() {
        showForm()
        showKeyboard(480)
        // Displayed first: a row that is clipped away reports an empty rect, which would pass the bounds check.
        tag("row-2").assertIsDisplayed()
        assertTrue(
            "the last row must sit above the keyboard without scrolling",
            tag("row-2").getBoundsInRoot().bottom <= keyboardTop(480),
        )
        tag("sheet-handle").assertIsDisplayed()
    }

    @Test
    fun when_even_the_lifted_sheet_is_too_short_the_body_scrolls_above_the_keyboard() {
        showForm()
        showKeyboard(700)
        tag("row-2").performScrollTo()
        tag("row-2").assertIsDisplayed()
        assertTrue(tag("row-2").getBoundsInRoot().bottom <= keyboardTop(700))
        tag("sheet-handle").assertIsDisplayed()
        text("Form").assertIsDisplayed()
    }

    /** Taller than the sheet's cap, so the body has to scroll. */
    private fun showLong() = compose.setContent {
        SheetScaffold(title = "Long sheet", onDismiss = {}) {
            repeat(30) { Box(Modifier.fillMaxWidth().height(60.dp).testTag("row-$it")) }
        }
    }

    @Test
    fun the_handle_and_title_stay_put_while_the_body_scrolls() {
        showLong()
        val handle = tag("sheet-handle").getBoundsInRoot()
        val title = text("Long sheet").getBoundsInRoot()
        tag("row-29").performScrollTo()
        tag("row-29").assertIsDisplayed()
        tag("sheet-handle").assertIsDisplayed()
        text("Long sheet").assertIsDisplayed()
        assertEquals(handle, tag("sheet-handle").getBoundsInRoot())
        assertEquals(title, text("Long sheet").getBoundsInRoot())
    }

    @Test
    fun a_short_sheet_wraps_its_content_rather_than_filling_the_cap() {
        compose.setContent { SheetScaffold(title = "Short", onDismiss = {}) { Box(Modifier.fillMaxWidth().height(40.dp).testTag("only")) } }
        val only = tag("only").getBoundsInRoot()
        val handle = tag("sheet-handle").getBoundsInRoot()
        // Header, one 40dp row and padding: far shorter than the 640dp cap.
        assertEquals(true, only.bottom - handle.top < 300.dp)
    }
}
