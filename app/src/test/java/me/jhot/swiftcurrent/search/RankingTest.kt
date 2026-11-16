package me.jhot.swiftcurrent.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

fun result(label: String, kind: Kind = Kind.App, source: String = "test") = SearchResult(
    id = "$kind:$label",
    label = label,
    sourceName = source,
    kind = kind,
    icon = null,
    primary = Action(label, null) { LaunchOutcome.Launched },
)

class RankingTest {
    private fun labels(query: String, vararg r: SearchResult) =
        Ranking.order(query, r.toList()).map { it.label }

    @Test
    fun blank_or_whitespace_query_returns_nothing() {
        assertEquals(emptyList<String>(), labels("", result("Maps")))
        assertEquals(emptyList<String>(), labels("   ", result("Maps")))
    }

    @Test
    fun accents_match_unaccented_query() {
        assertEquals(listOf("Café"), labels("cafe", result("Café"), result("Maps")))
    }

    @Test
    fun case_insensitive() {
        assertEquals(listOf("Maps"), labels("MAP", result("Maps")))
    }

    @Test
    fun apps_come_before_shortcuts_before_contacts() {
        val out = labels(
            "a",
            result("Aa other", Kind.Other),
            result("Aa contact", Kind.Contact),
            result("Aa shortcut", Kind.Shortcut),
            result("Aa app", Kind.App),
        )
        assertEquals(listOf("Aa app", "Aa shortcut", "Aa contact", "Aa other"), out)
    }

    @Test
    fun prefix_beats_substring() {
        val out = labels("map", result("Google Maps"), result("Bitmap"), result("Maps"))
        assertEquals(listOf("Maps", "Google Maps", "Bitmap"), out)
    }

    @Test
    fun ties_sort_alphabetically_and_non_matches_are_dropped() {
        assertEquals(listOf("Mail", "Maps"), labels("ma", result("Maps"), result("Mail"), result("Zed")))
    }
}

class MultiWordRankingTest {
    @Test
    fun every_word_must_start_a_word_of_the_label() {
        val r = listOf(result("Tasker: Bedtime"), result("Tasker: CreateAlarm"))
        assertEquals(listOf("Tasker: Bedtime"), Ranking.order("tasker bed", r).map { it.label })
    }
}

class RankingOptionsTest {
    private fun scores(vararg byLabel: Pair<String, Double>): LearnedScore {
        val m = byLabel.toMap()
        return LearnedScore { id, _ -> m[id.substringAfter(':')] ?: 0.0 }
    }

    private fun labels(options: RankingOptions, query: String, vararg r: SearchResult) =
        Ranking.order(query, r.toList(), options).map { it.label }

    private val group = "Messages: Josh Schanck, Matt Nunnelly"

    @Test
    fun contacts_first_puts_every_contact_above_shortcuts() {
        val input = arrayOf(
            result(group, Kind.Shortcut),
            result("Matt Nunnelly", Kind.Contact),
            result("Matt Example", Kind.Contact),
        )
        assertEquals(
            listOf("Matt Example", "Matt Nunnelly", group),
            labels(RankingOptions(contactsFirst = true), "matt", *input),
        )
        assertEquals(group, labels(RankingOptions(), "matt", *input).first())
    }

    @Test
    fun learned_lifts_within_group_when_apps_first() {
        val o = RankingOptions(appsFirst = true, learned = scores("Matt" to 5.0))
        assertEquals(listOf("Maps", "Matt"), labels(o, "ma", result("Maps"), result("Matt", Kind.Contact)))
    }

    @Test
    fun learned_lifts_across_groups_when_apps_first_off() {
        val o = RankingOptions(appsFirst = false, learned = scores("Matt" to 5.0))
        assertEquals(listOf("Matt", "Maps"), labels(o, "ma", result("Maps"), result("Matt", Kind.Contact)))
    }

    @Test
    fun learned_beats_contacts_first() {
        val o = RankingOptions(contactsFirst = true, learned = scores(group to 2.0))
        val out = labels(o, "matt", result(group, Kind.Shortcut), result("Matt Nunnelly", Kind.Contact))
        assertEquals(group, out.first())
    }

    @Test
    fun learned_orders_within_apps() {
        val o = RankingOptions(learned = scores("Mail" to 2.0))
        assertEquals(listOf("Mail", "Maps"), labels(o, "ma", result("Maps"), result("Mail")))
    }

    @Test
    fun score_is_computed_once_per_result() {
        var calls = 0
        val o = RankingOptions(learned = LearnedScore { _, _ -> calls++; 0.0 })
        val r = (1..50).map { result("App $it") }
        Ranking.order("app", r, o)
        assertEquals(50, calls)
    }
}

class EventRankingTest {
    private fun order(options: RankingOptions, vararg r: SearchResult) =
        Ranking.order("din", r.toList(), options).map { it.label }

    private val app = result("Dinner app", Kind.App)
    private val folded = result("Dinah contact", Kind.Contact).copy(folded = 1)
    private val plain = result("Dinah plain", Kind.Contact)
    private val shortcut = result("Dinner shortcut", Kind.Shortcut)
    private val event = result("Dinner event", Kind.Event)

    @Test
    fun event_ties_with_shortcut_and_sits_below_app_and_folded_contact() {
        for (contactsFirst in listOf(true, false)) {
            val o = RankingOptions(contactsFirst = contactsFirst)
            // The tie is broken by the label, as it is between two shortcuts.
            assertEquals(listOf("Dinner app", "Dinah contact", "Dinner event", "Dinner shortcut"), order(o, event, shortcut, folded, app))
        }
    }

    @Test
    fun event_ranks_like_a_shortcut_against_a_plain_contact() {
        // Contacts-first is the default: a plain contact beats both. Otherwise both beat it.
        assertEquals(listOf("Dinah plain", "Dinner event", "Dinner shortcut"), order(RankingOptions(contactsFirst = true), shortcut, event, plain))
        assertEquals(listOf("Dinner event", "Dinner shortcut", "Dinah plain"), order(RankingOptions(contactsFirst = false), plain, shortcut, event))
    }

    @Test
    fun a_learned_event_can_outrank_a_shortcut() {
        val o = RankingOptions(learned = LearnedScore { id, _ -> if (id == event.id) 3.0 else 0.0 })
        assertEquals(listOf("Dinner event", "Dinner shortcut"), order(o, shortcut, event))
    }
}

class EventDateOrderTest {
    private fun at(label: String, start: Long) = result(label, Kind.Event).copy(startMillis = start)
    private fun order(query: String, vararg r: SearchResult) = Ranking.order(query, r.toList()).map { it.label }

    @Test
    fun events_with_different_titles_go_soonest_first_not_alphabetical() {
        assertEquals(
            listOf("Jordan Haircut", "Jordan Dentist"),
            order("jordan", at("Jordan Dentist", 2000), at("Jordan Haircut", 1000)),
        )
    }

    @Test
    fun a_word_start_is_as_good_as_a_title_start_for_an_event() {
        // "Jordan Dentist" is sooner but only starts a later word; it must not sit below "Dentist Jordan".
        assertEquals(
            listOf("Jordan Dentist", "Dentist Jordan"),
            order("dentist", at("Dentist Jordan", 2000), at("Jordan Dentist", 1000)),
        )
    }

    @Test
    fun a_match_inside_a_word_still_ranks_below_a_word_start() {
        assertEquals(
            listOf("Dentist Jordan", "Accidental damage"),
            order("dent", at("Accidental damage", 1000), at("Dentist Jordan", 2000)),
        )
    }

    @Test
    fun an_event_with_a_start_precedes_a_tied_row_without_one() {
        // Mixing the two tie-breaks must stay one consistent order, or sorting can fail outright.
        val rows = listOf(result("Dinner shortcut", Kind.Shortcut), at("Dinner zzz", 5000), result("Dinner aaa", Kind.Shortcut), at("Dinner yyy", 1000))
        assertEquals(
            listOf("Dinner yyy", "Dinner zzz", "Dinner aaa", "Dinner shortcut"),
            Ranking.order("din", rows).map { it.label },
        )
    }

    @Test
    fun other_kinds_keep_prefix_above_word_start() {
        assertEquals(
            listOf("Dentist app", "Go dentist"),
            order("dent", result("Go dentist", Kind.App), result("Dentist app", Kind.App)),
        )
    }
}

class MatchQualityTest {
    @Test
    fun ignores_accents_case_and_trailing_space() {
        assertEquals(0, Ranking.matchQuality("cafe ", "Café"))
    }

    @Test
    fun grades_prefix_word_and_substring() {
        assertEquals(1, Ranking.matchQuality("den", "Go dentist"))
        assertEquals(2, Ranking.matchQuality("ent", "Dentist"))
    }

    @Test
    fun no_match_is_null() {
        assertNull(Ranking.matchQuality("zzz", "Dentist"))
    }

    @Test
    fun folded_variant_takes_already_normalized_text() {
        assertEquals(0, Ranking.matchQualityFolded("den", "dentist"))
    }
}
