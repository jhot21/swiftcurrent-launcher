package me.jhot.swiftcurrent.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticStrengthTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun a_name_stored_before_the_slider_becomes_the_nearest_strength() {
        val s = json.decodeFromString<Settings>("""{"scrubHaptic":"Normal","gestureHaptic":"Strong"}""")
        assertEquals(55, s.scrubHaptic)
        assertEquals(100, s.gestureHaptic)
        assertEquals(20, json.decodeFromString<Settings>("""{"scrubHaptic":"Light"}""").scrubHaptic)
    }

    @Test
    fun a_number_round_trips_and_a_stray_value_is_clamped_or_defaulted() {
        val s = Settings(scrubHaptic = 0, gestureHaptic = 65)
        assertEquals(s, json.decodeFromString<Settings>(json.encodeToString(Settings.serializer(), s)))
        assertEquals(100, json.decodeFromString<Settings>("""{"scrubHaptic":900}""").scrubHaptic)
        assertEquals(HapticStrength.DEFAULT, json.decodeFromString<Settings>("""{"scrubHaptic":"Loud"}""").scrubHaptic)
        assertEquals(HapticStrength.DEFAULT, json.decodeFromString<Settings>("{}").scrubHaptic)
    }
}
