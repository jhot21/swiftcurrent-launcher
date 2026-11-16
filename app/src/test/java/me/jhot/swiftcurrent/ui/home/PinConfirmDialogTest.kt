package me.jhot.swiftcurrent.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class PinConfirmDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun res(id: Int) = compose.activity.getString(id)

    @Test
    fun it_shows_the_title_and_label_and_reports_each_button() {
        val calls = mutableListOf<String>()
        compose.setContent { PinConfirmDialog("Chat", icon = null, onAdd = { calls += "add" }, onCancel = { calls += "cancel" }) }
        compose.onNodeWithText(res(R.string.pin_title)).assertExists()
        compose.onNodeWithText("Chat").assertExists()
        compose.onNodeWithText(res(R.string.pin_add)).performClick()
        compose.onNodeWithText(res(R.string.pin_cancel)).performClick()
        assertEquals(listOf("add", "cancel"), calls)
    }

    @Test
    fun an_empty_label_falls_back_to_a_neutral_name() {
        compose.setContent { PinConfirmDialog("  ", icon = null, onAdd = {}, onCancel = {}) }
        compose.onNodeWithText(res(R.string.label_unknown_shortcut)).assertExists()
    }
}
