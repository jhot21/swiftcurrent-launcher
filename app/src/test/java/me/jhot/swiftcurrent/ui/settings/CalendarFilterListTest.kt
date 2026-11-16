package me.jhot.swiftcurrent.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.agenda.CalendarInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CalendarFilterListTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calendars = listOf(
        CalendarInfo(1, "Personal", 0x2E7D32, "me@example.com"),
        CalendarInfo(2, "Work", 0xD50000, "work@example.com"),
    )

    @Test
    fun null_shows_every_calendar_on_with_name_and_account() {
        compose.setContent { CalendarFilterList(calendars, null, {}) }
        compose.onNodeWithText("Calendars").assertIsDisplayed()
        compose.onNodeWithText("Personal").assertIsDisplayed()
        compose.onNodeWithText("work@example.com").assertIsDisplayed()
        compose.onNodeWithTag("calendar-toggle-1").assertIsOn()
        compose.onNodeWithTag("calendar-toggle-2").assertIsOn()
    }

    @Test
    fun a_set_switches_the_others_off_and_a_tap_reports_the_id() {
        val toggled = mutableListOf<Long>()
        compose.setContent { CalendarFilterList(calendars, setOf(1L), { toggled += it }) }
        compose.onNodeWithTag("calendar-toggle-1").assertIsOn()
        compose.onNodeWithTag("calendar-toggle-2").assertIsOff()
        compose.onNodeWithTag("calendar-toggle-2").performClick()
        assertEquals(listOf(2L), toggled)
    }

    @Test
    fun no_calendars_says_so() {
        compose.setContent { CalendarFilterList(emptyList(), null, {}) }
        compose.onNodeWithText("No calendars found").assertIsDisplayed()
    }
}
