package me.jhot.swiftcurrent.search

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactFoldingTest {
    private val launched = mutableListOf<String>()

    private fun act(label: String) = Action(label, null) { launched += label; LaunchOutcome.Launched }

    private fun contact(name: String) = SearchResult(
        "contact:$name", name, "Contacts", Kind.Contact, null, act(name), secondary = listOf(act("Call")),
    )

    private fun shortcut(app: String, entry: String, pkg: String? = null) = SearchResult(
        "shortcut:$app:$entry", "$app: $entry", "Shortcuts", Kind.Shortcut, null, act("$app: $entry"),
        appLabel = app, entryLabel = entry, packageName = pkg,
    )

    /** A contact with its own Call and Message buttons, keyed as ContactSource keys them. */
    private fun reachable(name: String, vararg methods: String) = SearchResult(
        "contact:$name", name, "Contacts", Kind.Contact, null, act(name),
        secondary = methods.map { Action(it.replaceFirstChar(Char::uppercase), null, recentId = "contact:1|$it|+15550100") { LaunchOutcome.Launched } },
    )

    private val defaults = DefaultApps(sms = "pkg.sms", dialer = "pkg.dialer")

    private fun runWith(query: String, vararg results: SearchResult) =
        ContactFolding.foldIntoTop(Ranking.order(query, ContactFolding.mark(results.toList())), defaults)

    private val app = SearchResult("app:x", "Alex App", "Apps", Kind.App, null, act("Alex App"))

    /** What the engine does: mark, rank, then fold into the top result. */
    private fun run(query: String, vararg results: SearchResult) =
        ContactFolding.foldIntoTop(Ranking.order(query, ContactFolding.mark(results.toList())))

    @Test
    fun shortcuts_named_after_the_top_contact_become_its_actions_and_leave_the_list() {
        val out = run("alex", shortcut("Gmail", "Alex Example"), shortcut("Meet", "Alex Example"), contact("Alex Example"))
        val c = out.single()
        assertEquals(Kind.Contact, c.kind)
        assertEquals(listOf("Call", "Gmail", "Meet"), c.secondary.map { it.label })
        c.secondary[1].perform()
        assertEquals(listOf("Gmail: Alex Example"), launched)
    }

    @Test
    fun moved_shortcut_actions_carry_the_shortcut_id() {
        val out = run("alex", shortcut("Gmail", "Alex Example"), shortcut("Meet", "Alex Example"), contact("Alex Example"))
        // The contact's own Call is not a moved shortcut, so it has none.
        assertEquals(listOf(null, "shortcut:Gmail:Alex Example", "shortcut:Meet:Alex Example"), out.single().secondary.map { it.recentId })
    }

    @Test
    fun matching_ignores_case_and_accents() {
        val out = run("alex", shortcut("Gmail", "ALEX éxample"), contact("Alex Example"))
        assertEquals(1, out.size)
        assertEquals(listOf("Call", "Gmail"), out.single().secondary.map { it.label })
    }

    @Test
    fun a_contact_that_is_not_on_top_keeps_its_shortcuts_as_rows() {
        // Two people match; both have shortcuts so both outrank plain shortcuts. Only the top one folds.
        val out = run(
            "matt",
            contact("Matt Alpha"), contact("Matt Beta"),
            shortcut("Gmail", "Matt Alpha"), shortcut("Gmail", "Matt Beta"),
        )
        assertEquals(listOf("Matt Alpha", "Matt Beta", "Gmail: Matt Beta"), out.map { it.label })
        assertEquals(listOf("Call", "Gmail"), out[0].secondary.map { it.label })
        assertEquals(listOf("Call"), out[1].secondary.map { it.label })
    }

    @Test
    fun nothing_folds_when_the_top_result_is_not_a_contact() {
        val out = run("alex", app, shortcut("Gmail", "Alex Example"), contact("Alex Example"))
        assertEquals(listOf("Alex App", "Alex Example", "Gmail: Alex Example"), out.map { it.label })
        assertEquals(listOf("Call"), out[1].secondary.map { it.label })
    }

    @Test
    fun a_shortcut_with_no_matching_contact_stays() {
        val out = run("alex", shortcut("Tasker", "Alex Timer"), shortcut("Gmail", "Someone Else"), contact("Alex Example"))
        assertEquals(listOf("Tasker: Alex Timer", "Alex Example"), out.map { it.label })
    }

    @Test
    fun without_any_contact_nothing_changes() {
        val input = listOf(shortcut("Gmail", "Alex Example"), app)
        assertEquals(input, ContactFolding.foldIntoTop(ContactFolding.mark(input)))
    }

    @Test
    fun a_contact_with_shortcuts_ranks_above_remaining_shortcuts_but_below_apps() {
        val ordered = Ranking.order(
            "alex",
            ContactFolding.mark(
                listOf(
                    shortcut("Tasker", "Alex Timer"), shortcut("Gmail", "Alex Example"), contact("Alex Example"), app,
                ),
            ),
        )
        assertEquals(listOf("Alex App", "Alex Example", "Gmail: Alex Example", "Tasker: Alex Timer"), ordered.map { it.label })
    }

    @Test
    fun a_contact_with_no_shortcuts_still_ranks_after_shortcuts() {
        val ordered = Ranking.order("alex", ContactFolding.mark(listOf(contact("Alex Example"), shortcut("Tasker", "Alex Timer"))))
        assertEquals(listOf("Tasker: Alex Timer", "Alex Example"), ordered.map { it.label })
    }

    @Test
    fun a_name_an_app_cut_short_with_an_ellipsis_still_names_the_contact_it_starts() {
        val out = run("alex", shortcut("Contacts", "Alex Exam\u2026"), contact("Alex Example"))
        assertEquals(1, out.size)
        assertEquals(listOf("Call", "Contacts"), out.single().secondary.map { it.label })
    }

    @Test
    fun a_cut_name_that_starts_two_contacts_is_left_alone() {
        val out = run("alex", shortcut("Contacts", "Alex Exam\u2026"), contact("Alex Example"), contact("Alex Examiner"))
        assertEquals(3, out.size)
    }

    @Test
    fun a_very_short_cut_is_too_vague_to_match() {
        val out = run("al", shortcut("Contacts", "Al\u2026"), contact("Alex Example"))
        assertEquals(2, out.size)
    }

    @Test
    fun two_contacts_with_the_same_name_do_not_take_a_shortcut_named_after_them() {
        val first = contact("Alex Example")
        val second = contact("Alex Example").copy(id = "contact:other")
        val out = run("alex", first, second, shortcut("Gmail", "Alex Example"))
        // Which person the shortcut means is unknowable, so it stays a row and neither contact folds it.
        assertEquals(3, out.size)
        assertEquals(listOf("Call"), out.filter { it.kind == Kind.Contact }.flatMap { it.secondary.map { a -> a.label } }.distinct())
    }

    @Test
    fun a_shortcut_of_the_default_sms_or_dialer_app_is_dropped_when_the_contact_has_its_own_button_for_it() {
        val out = runWith(
            "alex",
            reachable("Alex Example", "call", "message"),
            shortcut("Messages", "Alex Example", "pkg.sms"), shortcut("Phone", "Alex Example", "pkg.dialer"), shortcut("Voice", "Alex Example", "pkg.voice"),
        )
        assertEquals(listOf("Call", "Message", "Voice"), out.single().secondary.map { it.label })
    }

    @Test
    fun a_default_app_shortcut_stays_when_the_contact_has_no_button_that_covers_it() {
        val out = runWith(
            "alex",
            reachable("Alex Example", "email"),
            shortcut("Messages", "Alex Example", "pkg.sms"), shortcut("Phone", "Alex Example", "pkg.dialer"),
        )
        assertEquals(listOf("Email", "Messages", "Phone"), out.single().secondary.map { it.label })
    }

    @Test
    fun the_sms_shortcut_is_only_covered_by_message_and_the_dialer_only_by_call() {
        val out = runWith(
            "alex",
            reachable("Alex Example", "message"),
            shortcut("Messages", "Alex Example", "pkg.sms"), shortcut("Phone", "Alex Example", "pkg.dialer"),
        )
        assertEquals(listOf("Message", "Phone"), out.single().secondary.map { it.label })
    }

    @Test
    fun a_dropped_shortcut_does_not_come_back_as_a_row() {
        val out = runWith("alex", reachable("Alex Example", "message"), shortcut("Messages", "Alex Example", "pkg.sms"))
        assertEquals(1, out.size)
    }

    @Test
    fun without_known_defaults_nothing_is_dropped() {
        val out = run("alex", reachable("Alex Example", "call", "message"), shortcut("Messages", "Alex Example", "pkg.sms"))
        assertEquals(listOf("Call", "Message", "Messages"), out.single().secondary.map { it.label })
    }
}
