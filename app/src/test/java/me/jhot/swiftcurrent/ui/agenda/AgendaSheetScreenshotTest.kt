package me.jhot.swiftcurrent.ui.agenda

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.agenda.AgendaDay
import me.jhot.swiftcurrent.agenda.AgendaEvent
import me.jhot.swiftcurrent.ui.ScreenshotBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// The sheet layout baseline: re-record only when the design is meant to change.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class AgendaSheetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("America/Denver")
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone).toInstant()

    @Test
    fun agendaSheet_days() {
        val dentist = AgendaEvent("Dentist", at(8, 0), at(9, 0), true, null, 0x9C27B0, 2)
        val standup = AgendaEvent("Standup", at(9, 11, 30), at(9, 12), false, null, 0x2E7D32, 1)
        val sync = AgendaEvent("Team sync", at(9, 18), at(9, 19), false, "Room 4", 0xD50000, 1)
        val days = listOf(
            AgendaDay(LocalDate.of(2026, 11, 8), "today", listOf(dentist)),
            AgendaDay(LocalDate.of(2026, 11, 9), "tomorrow", listOf(standup, sync)),
        )
        compose.setContent { ScreenshotBackdrop { AgendaSheet(days, {}, {}, at(8, 22, 23), zone) } }
        compose.onRoot().captureRoboImage("src/test/snapshots/agendaSheet_days.png")
    }
}
