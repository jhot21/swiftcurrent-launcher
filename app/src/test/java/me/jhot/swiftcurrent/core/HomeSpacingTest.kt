package me.jhot.swiftcurrent.core

import kotlinx.serialization.json.Json
import me.jhot.swiftcurrent.ui.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSpacingTest {
    @Test
    fun the_default_is_what_home_always_used_so_nothing_moves_for_anyone_who_never_opens_the_setting() {
        assertEquals(HomeLayoutDefaults.BOTTOM_GAP_DP, HomeSpacing.DEFAULT.toFloat(), 0f)
        assertEquals(HomeSpacing.DEFAULT, Settings().bottomGapDp)
    }

    @Test
    fun a_value_is_brought_into_range_and_onto_a_step() {
        assertEquals(HomeSpacing.MIN, HomeSpacing.clamp(-50))
        assertEquals(HomeSpacing.LIMIT, HomeSpacing.clamp(100_000))
        assertEquals(32, HomeSpacing.clamp(33))
        assertEquals(36, HomeSpacing.clamp(39))
        assertEquals(0, HomeSpacing.clamp(3))
        // Whatever comes in, what comes out is a legal value.
        for (x in -20..1200) {
            val c = HomeSpacing.clamp(x)
            assertTrue(c in HomeSpacing.MIN..HomeSpacing.LIMIT && c % HomeSpacing.STEP == 0)
        }
    }

    @Test
    fun older_data_without_the_setting_decodes_with_the_default() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<Settings>("""{"sortByUsage":false}""")
        assertEquals(HomeSpacing.DEFAULT, old.bottomGapDp)
        assertEquals(false, old.sortByUsage)
    }

    @Test
    fun the_setting_survives_a_round_trip_through_the_stored_form() {
        val json = Json
        val text = json.encodeToString(Settings.serializer(), Settings(bottomGapDp = 64))
        assertEquals(64, json.decodeFromString<Settings>(text).bottomGapDp)
    }
}
