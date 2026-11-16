package me.jhot.swiftcurrent.agenda

import me.jhot.swiftcurrent.search.LaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationIntentsTest {
    private val view = "android.intent.action.VIEW"

    @Test
    fun a_bare_https_url_opens_as_a_link() {
        assertEquals(listOf(LocationIntentSpec(view, "https://zoom.example/j/123")), LocationIntents.specs("https://zoom.example/j/123"))
    }

    @Test
    fun a_url_inside_other_text_opens_the_url() {
        assertEquals(
            listOf(LocationIntentSpec(view, "https://zoom.example/j/1?pwd=x")),
            LocationIntents.specs("Zoom: https://zoom.example/j/1?pwd=x (passcode in invite)"),
        )
    }

    @Test
    fun trailing_punctuation_is_trimmed_from_the_url() {
        assertEquals("https://example.com/room", LocationIntents.specs("Join at https://example.com/room.").single().uri)
        assertEquals("https://example.com/room", LocationIntents.specs("(https://example.com/room)").single().uri)
    }

    @Test
    fun a_street_address_becomes_a_geo_query_then_a_web_maps_url() {
        assertEquals(
            listOf(
                LocationIntentSpec(view, "geo:0,0?q=Osteria%20Rosa%2C%20120%20Main%20St"),
                LocationIntentSpec(view, "https://www.google.com/maps/search/?api=1&query=Osteria%20Rosa%2C%20120%20Main%20St"),
            ),
            LocationIntents.specs("Osteria Rosa, 120 Main St"),
        )
    }

    @Test
    fun blank_location_yields_nothing() {
        assertEquals(emptyList<LocationIntentSpec>(), LocationIntents.specs("  \n "))
    }

    @Test
    fun newlines_collapse_to_spaces() {
        assertEquals("geo:0,0?q=Osteria%20Rosa%20120%20Main%20St", LocationIntents.specs("Osteria Rosa\n120 Main St").first().uri)
    }

    @Test
    fun perform_falls_back_to_the_next_spec_when_the_first_fails() {
        val tried = mutableListOf<String>()
        val outcome = LocationIntents.perform(LocationIntents.specs("12 Elm St")) { spec ->
            tried += spec.uri.substringBefore(':')
            if (spec.uri.startsWith("geo:")) LaunchOutcome.Failed("none") else LaunchOutcome.Launched
        }
        assertEquals(LaunchOutcome.Launched, outcome)
        assertEquals(listOf("geo", "https"), tried)
    }

    @Test
    fun perform_reports_failure_when_none_launch() {
        val outcome = LocationIntents.perform(LocationIntents.specs("12 Elm St")) { LaunchOutcome.Failed("no app") }
        assertEquals(LaunchOutcome.Failed("no app"), outcome)
    }

    @Test
    fun perform_stops_at_the_first_launch() {
        var calls = 0
        LocationIntents.perform(LocationIntents.specs("12 Elm St")) { calls++; LaunchOutcome.Launched }
        assertEquals(1, calls)
    }

    @Test
    fun perform_with_nothing_to_try_is_not_a_failure() {
        assertEquals(LaunchOutcome.Launched, LocationIntents.perform(emptyList()) { LaunchOutcome.Failed("x") })
    }
}
