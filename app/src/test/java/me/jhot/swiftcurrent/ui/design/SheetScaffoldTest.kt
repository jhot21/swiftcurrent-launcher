package me.jhot.swiftcurrent.ui.design

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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SheetScaffoldTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // The sheet is clickable (it swallows scrim taps), which merges everything inside it.
    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)

    private fun text(t: String) = compose.onNodeWithText(t, useUnmergedTree = true)

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
