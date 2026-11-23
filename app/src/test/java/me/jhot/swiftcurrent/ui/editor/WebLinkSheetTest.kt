package me.jhot.swiftcurrent.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Renames
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class WebLinkSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<Pair<String, String>>()

    // The sheet is clickable (it swallows scrim taps), which merges everything inside it.
    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)

    private fun show(clipboardUrl: String? = null, browser: String = "Firefox") = compose.setContent {
        WebLinkSheet(browser, clipboardUrl, onSave = { u, n -> saved += u to n }, onDismiss = {})
    }

    private fun SemanticsNodeInteraction.typedLength() =
        fetchSemanticsNode().config[SemanticsProperties.EditableText].text.length

    @Test
    fun the_title_names_the_browser() {
        show(browser = "Firefox")
        compose.onNodeWithText(compose.activity.getString(R.string.web_link_title, "Firefox")).assertExists()
    }

    @Test
    fun save_stays_disabled_until_the_address_is_valid_and_a_tap_on_it_saves_nothing() {
        show()
        tag("web-link-save").assertIsNotEnabled().performClick()
        tag("web-link-url").performTextInput("ftp://x")
        tag("web-link-save").assertIsNotEnabled().performClick()
        assertEquals(emptyList<Pair<String, String>>(), saved)
    }

    @Test
    fun a_valid_address_enables_save_and_reports_what_was_typed() {
        show()
        tag("web-link-url").performTextInput("example.com")
        tag("web-link-name").performTextInput("Example")
        tag("web-link-save").assertIsEnabled().performClick()
        assertEquals(listOf("example.com" to "Example"), saved)
    }

    @Test
    fun an_invalid_address_shows_an_error_but_an_empty_field_does_not() {
        show()
        tag("web-link-error").assertDoesNotExist()
        tag("web-link-url").performTextInput("javascript:alert(1)")
        tag("web-link-error").assertExists()
    }

    @Test
    fun the_name_hint_is_the_host_and_a_blank_name_is_reported_as_blank() {
        show()
        tag("web-link-name-hint").assertDoesNotExist()
        tag("web-link-url").performTextInput("https://Example.com/path")
        tag("web-link-name-hint").assertTextEquals("example.com")
        tag("web-link-save").performClick()
        assertEquals(listOf("https://Example.com/path" to ""), saved)
    }

    @Test
    fun a_web_address_on_the_clipboard_prefills_the_field() {
        show(clipboardUrl = "https://example.com/x")
        tag("web-link-url").assertTextEquals("https://example.com/x")
    }

    @Test
    fun clipboard_text_that_is_not_a_web_address_leaves_the_field_empty_without_an_error() {
        show(clipboardUrl = "hello there")
        tag("web-link-url").assertTextEquals("")
        tag("web-link-error").assertDoesNotExist()
    }

    @Test
    fun a_clipboard_link_with_another_scheme_is_ignored() {
        show(clipboardUrl = "ftp://x.org")
        tag("web-link-url").assertTextEquals("")
    }

    @Test
    fun the_fields_stop_accepting_text_past_their_limits() {
        show()
        tag("web-link-url").performTextInput("a".repeat(5000))
        tag("web-link-name").performTextInput("b".repeat(5000))
        assertEquals(2048, tag("web-link-url").typedLength())
        assertEquals(Renames.MAX_LENGTH, tag("web-link-name").typedLength())
    }

    @Test
    fun what_was_typed_survives_a_recreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WebLinkSheet("Firefox", null, onSave = { _, _ -> }, onDismiss = {}) }
        tag("web-link-url").performTextInput("example.com")
        tag("web-link-name").performTextInput("Example")
        restoration.emulateSavedInstanceStateRestore()
        tag("web-link-url").assertTextEquals("example.com")
        tag("web-link-name").assertTextEquals("Example")
    }
}
