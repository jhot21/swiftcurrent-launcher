package me.jhot.swiftcurrent.search

import me.jhot.swiftcurrent.search.sources.ContactActionKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactHidingTest {
    private fun act(label: String, recentId: String?) = Action(label, null, recentId) { LaunchOutcome.Launched }

    private fun contact(id: Long, vararg actions: Action) = SearchResult(
        "contact:$id", "Contact $id", "Contacts", Kind.Contact, null, act("Contact $id", null), secondary = actions.toList(),
    )

    private val meetVideo = ContactActionKey.ofApp(1, "x.meet", "a@example.com")
    private val meetVoice = ContactActionKey.ofApp(1, "x.meet.audio", "a@example.com")

    private fun hidden(vararg pairs: Pair<String, String>) = pairs.map { ContactHiding.key(it.first, it.second) }.toSet()

    private fun labels(r: SearchResult) = r.secondary.map { it.label }

    @Test
    fun only_the_hidden_action_of_that_contact_goes() {
        val c = contact(1, act("Video", meetVideo), act("Voice", meetVoice))
        val out = ContactHiding.apply(listOf(c), hidden("contact:1" to meetVideo)).single()
        assertEquals(listOf("Voice"), labels(out))
    }

    @Test
    fun the_same_action_id_under_another_contact_stays() {
        val a = contact(1, act("Video", "shortcut:k"))
        val b = contact(2, act("Video", "shortcut:k"))
        val out = ContactHiding.apply(listOf(a, b), hidden("contact:1" to "shortcut:k"))
        assertEquals(listOf(emptyList(), listOf("Video")), out.map(::labels))
    }

    @Test
    fun hiding_every_action_keeps_the_contact_and_its_primary() {
        val c = contact(1, act("Call", "contact:1|call|1"))
        val out = ContactHiding.apply(listOf(c), hidden("contact:1" to "contact:1|call|1")).single()
        assertEquals(emptyList<String>(), labels(out))
        assertEquals(c.primary, out.primary)
    }

    @Test
    fun results_that_are_not_contacts_and_actions_without_an_id_are_left_alone() {
        val shortcut = SearchResult("shortcut:k", "A: B", "Shortcuts", Kind.Shortcut, null, act("A: B", null), secondary = listOf(act("x", "shortcut:k")))
        val c = contact(1, act("Plain", null))
        val out = ContactHiding.apply(listOf(shortcut, c), hidden("contact:1" to "shortcut:k"))
        assertEquals(listOf(listOf("x"), listOf("Plain")), out.map(::labels))
    }

    @Test
    fun nothing_hidden_returns_the_same_list() {
        val list = listOf(contact(1, act("Call", "contact:1|call|1")))
        assertEquals(list, ContactHiding.apply(list, emptySet()))
    }
}
