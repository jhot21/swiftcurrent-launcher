package me.jhot.swiftcurrent.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClockTextTest {
    private val time = ZonedDateTime.of(2026, 11, 8, 8, 40, 0, 0, ZoneId.of("UTC"))
    private val afternoon = time.withHour(15)

    @Test
    fun clock_is_hour_and_minute_without_a_day_period() {
        assertEquals("8:40", ClockText.clock(time, Locale.US, is24Hour = false))
        assertEquals("3:40", ClockText.clock(afternoon, Locale.US, is24Hour = false))
        assertEquals("15:40", ClockText.clock(afternoon, Locale.US, is24Hour = true))
        assertEquals("8:40", ClockText.clock(time, Locale.US, is24Hour = true))
    }

    @Test
    fun date_is_weekday_month_and_day_in_the_device_locale() {
        assertEquals("Sun, Nov 8", ClockText.date(time, Locale.US))
        assertEquals("So., 8. Nov.", ClockText.date(time, Locale.GERMANY))
    }
}
