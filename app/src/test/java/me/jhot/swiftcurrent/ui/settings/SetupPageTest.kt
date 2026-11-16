package me.jhot.swiftcurrent.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.jhot.swiftcurrent.R
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
class SetupPageTest {
    @get:Rule
    val compose = createComposeRule()

    private fun res(id: Int, vararg a: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)

    private val kinds = listOf(StepKind.DefaultLauncher, StepKind.Contacts, StepKind.Calendar, StepKind.Accessibility)

    private fun show() = compose.setContent {
        SetupPage(kinds.map { SetupStep(it, done = false) }, blocked = emptySet(), onAction = {}, onFinish = {})
    }

    @Test
    fun every_action_button_is_a_button_that_names_its_step() {
        show()
        val expected = mapOf(
            StepKind.DefaultLauncher to res(R.string.setup_action_for, res(R.string.setup_action_open), res(R.string.setup_default_title)),
            StepKind.Contacts to res(R.string.setup_action_for, res(R.string.setup_action_allow), res(R.string.setup_contacts_title)),
            StepKind.Calendar to res(R.string.setup_action_for, res(R.string.setup_action_allow), res(R.string.setup_calendar_title)),
            StepKind.Accessibility to res(R.string.setup_action_for, res(R.string.setup_action_open), res(R.string.setup_lock_title)),
        )
        for (kind in kinds) {
            val config = compose.onNodeWithTag("setup-action-$kind").fetchSemanticsNode().config
            assertEquals(Role.Button, config[SemanticsProperties.Role])
            assertEquals(listOf(expected.getValue(kind)), config[SemanticsProperties.ContentDescription])
        }
        assertEquals("the three Allow-style buttons are told apart", 4, expected.values.toSet().size)
    }

    @Test
    fun every_action_button_has_a_48dp_touch_target() {
        show()
        for (kind in kinds) {
            val bounds = compose.onNodeWithTag("setup-action-$kind").getBoundsInRoot()
            assertTrue("$kind ${bounds.width} x ${bounds.height}", bounds.width.value >= 47.9f && bounds.height.value >= 47.9f)
        }
    }
}
