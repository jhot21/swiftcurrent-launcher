package me.jhot.swiftcurrent.agenda

import me.jhot.swiftcurrent.core.LauncherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarFilterTest {
    private val all = listOf(1L, 2L, 3L)

    @Test
    fun null_means_every_calendar_is_on() {
        assertTrue(CalendarFilter.isEnabled(null, 99))
        assertTrue(CalendarFilter.isEnabled(setOf(1L), 1))
        assertFalse(CalendarFilter.isEnabled(setOf(1L), 2))
        assertFalse(CalendarFilter.isEnabled(emptySet(), 1))
    }

    @Test
    fun switching_one_off_from_all_materializes_the_rest() {
        assertEquals(setOf(1L, 3L), CalendarFilter.toggle(null, 2, all))
    }

    @Test
    fun switching_everything_back_on_returns_to_null() {
        assertNull(CalendarFilter.toggle(setOf(1L, 3L), 2, all))
        assertEquals(setOf(1L, 2L), CalendarFilter.toggle(setOf(1L), 2, all))
    }

    @Test
    fun the_last_calendar_can_be_switched_off_to_an_empty_set() {
        assertEquals(emptySet<Long>(), CalendarFilter.toggle(setOf(1L), 1, all))
    }

    @Test
    fun a_calendar_added_later_is_not_forced_on_by_a_stale_set() {
        // 4 appeared after the owner narrowed the list; the set stays what the owner chose.
        assertEquals(setOf(1L), CalendarFilter.toggle(setOf(1L, 2L), 2, listOf(1L, 2L, 4L)))
    }

    @Test
    fun apply_writes_only_the_enabled_calendars_setting() {
        val data = LauncherData()
        val next = CalendarFilter.apply(data, 2, all)
        assertEquals(setOf(1L, 3L), next.settings.enabledCalendars)
        assertEquals(data.copy(settings = data.settings.copy(enabledCalendars = setOf(1L, 3L))), next)
    }

    @Test
    fun applying_to_search_writes_only_the_search_calendars_setting() {
        val data = LauncherData()
        val next = CalendarFilter.applyToSearch(data, 2, all)
        assertEquals(setOf(1L, 3L), next.settings.searchCalendars)
        assertNull(next.settings.enabledCalendars)
        assertEquals(data.copy(settings = data.settings.copy(searchCalendars = setOf(1L, 3L))), next)
    }

    @Test
    fun applying_to_the_agenda_leaves_search_calendars_alone() {
        val data = LauncherData(settings = LauncherData().settings.copy(searchCalendars = setOf(1L)))
        assertEquals(setOf(1L), CalendarFilter.apply(data, 2, all).settings.searchCalendars)
    }

    @Test
    fun older_settings_without_search_calendars_decode_to_every_calendar() {
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<me.jhot.swiftcurrent.core.Settings>("{}")
        assertNull(old.searchCalendars)
    }
}
