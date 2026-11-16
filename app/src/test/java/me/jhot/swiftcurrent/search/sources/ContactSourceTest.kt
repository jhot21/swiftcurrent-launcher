package me.jhot.swiftcurrent.search.sources

import android.content.Intent
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

private class FakeReader(var rows: List<ContactRow>) : ContactReader {
    var calls = 0
    val contactQueries = mutableListOf<Set<Long>>()
    val threads = mutableListOf<Thread>()
    override fun rows(query: String): List<ContactRow> { calls++; return rows }
    override fun rowsForContacts(ids: Set<Long>): List<ContactRow> {
        contactQueries += ids
        threads += Thread.currentThread()
        return rows.filter { it.contactId in ids }
    }
}

private class RecordingFactory : ContactActionFactory {
    val launched = mutableListOf<ContactIntentSpec>()
    override fun launch(spec: ContactIntentSpec): LaunchOutcome { launched += spec; return LaunchOutcome.Launched }
}

private const val PHONE = "vnd.android.cursor.item/phone_v2"
private const val EMAIL = "vnd.android.cursor.item/email_v2"
private const val WA = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
private const val SIGNAL_MSG = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
private const val SIGNAL_VOICE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"
private const val SIGNAL_VIDEO = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.videocall"
private const val WA_VOICE = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
private const val WA_VIDEO = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
private const val NAME = "Alex Example"
private const val MATT = "Matt Lastname"

class ContactSourceTest {
    private fun search(rows: List<ContactRow>, factory: ContactActionFactory = RecordingFactory(), access: Boolean = true) =
        runBlocking { ContactSource(FakeReader(rows), { access }, EnglishSearchStrings, actionFactory = factory).search("alex") }

    @Test
    fun contact_with_phone_email_and_whatsapp_rows_gets_three_actions() {
        val r = search(
            listOf(
                ContactRow(1, NAME, PHONE, "+15550100"),
                ContactRow(1, NAME, EMAIL, "alex@example.com"),
                ContactRow(1, NAME, WA, "15550100@s.whatsapp.net", rowId = 42, appName = "WhatsApp", text = "Message +15550100"),
            ),
        ).single()
        assertEquals(NAME, r.label)
        assertEquals("Contacts", r.sourceName)
        // Rule: Call and Message for a phone row, Email for an email row, WhatsApp for a WhatsApp row.
        assertEquals(listOf("Call", "Message", "Email", "WhatsApp Message"), r.secondary.map { it.label })
    }

    @Test
    fun each_contact_method_has_a_stable_key() {
        val rows = listOf(
            ContactRow(1, NAME, PHONE, "+15550100"),
            ContactRow(1, NAME, EMAIL, "alex@example.com"),
            ContactRow(1, NAME, WA, "15550100@s.whatsapp.net", rowId = 42, appName = "WhatsApp", text = "Message +15550100"),
        )
        val r = search(rows).single()
        // Call and Message share a number but are different picks.
        assertEquals(
            listOf(
                "contact:1|call|+15550100",
                "contact:1|message|+15550100",
                "contact:1|email|alex@example.com",
                "contact:1|whatsapp|15550100@s.whatsapp.net",
            ),
            r.secondary.map { it.recentId },
        )
        assertEquals(null, r.primary.recentId)
        // The same rows give the same keys on the next search.
        assertEquals(r.secondary.map { it.recentId }, search(rows).single().secondary.map { it.recentId })
    }

    @Test
    fun contact_with_no_matching_rows_has_no_secondary_actions_and_does_not_crash() {
        val factory = RecordingFactory()
        val r = search(listOf(ContactRow(2, NAME, "vnd.android.cursor.item/note", "hi")), factory).single()
        assertTrue(r.secondary.isEmpty())
        assertEquals(LaunchOutcome.Launched, r.primary.perform())
        assertEquals("content://com.android.contacts/contacts/2", factory.launched.single().uri)
    }

    @Test
    fun contacts_denied_returns_empty_without_reading() {
        val reader = FakeReader(listOf(ContactRow(1, NAME, PHONE, "+15550100")))
        val denied = ContactSource(reader, { false }, EnglishSearchStrings, actionFactory = RecordingFactory())
        assertTrue(runBlocking { denied.search("alex") }.isEmpty())
        assertEquals(0, reader.calls)
    }

    @Test
    fun whatsapp_action_uses_the_data_row_uri_not_a_wa_me_link() {
        val factory = RecordingFactory()
        // DATA1 of a WhatsApp profile row is a JID, not a URI: the launch is the data row itself.
        val r = search(listOf(ContactRow(1, NAME, WA, "15550100@s.whatsapp.net", rowId = 42, appName = "WhatsApp", text = "Message +15550100")), factory).single()
        r.secondary.single().perform()
        val spec = factory.launched.single()
        assertEquals(Intent.ACTION_VIEW, spec.action)
        assertEquals("content://com.android.contacts/data/42", spec.uri)
        assertEquals(WA, spec.mimetype)
        assertFalse(spec.toString().contains("wa.me"))
        assertFalse(spec.toString().contains("whatsapp.net"))
    }

    private fun app(contact: Long, mimetype: String, name: String, text: String?, rowId: Long, data: String = "+15550100") =
        ContactRow(contact, NAME, mimetype, data, rowId, appName = name, text = text)

    private val signalAndWhatsApp = listOf(
        ContactRow(1, NAME, PHONE, "+15550100"),
        app(1, SIGNAL_MSG, "Signal", "Message +15550100", 1),
        app(1, SIGNAL_VOICE, "Signal", "Signal Voice Call +15550100", 2),
        app(1, SIGNAL_VIDEO, "Signal", "Signal Video Call +15550100", 3),
        app(1, WA, "WhatsApp", "Message +1 555-0100", 4, "15550100@s.whatsapp.net"),
        app(1, WA_VOICE, "WhatsApp", "Voice call +1 555-0100", 5, "15550100@s.whatsapp.net"),
        app(1, WA_VIDEO, "WhatsApp", "Video call +1 555-0100", 6, "15550100@s.whatsapp.net"),
    )

    @Test
    fun every_connected_app_row_is_its_own_button_grouped_by_app() {
        val factory = RecordingFactory()
        val r = search(signalAndWhatsApp, factory).single()
        assertEquals(
            listOf("Call", "Message", "Signal Message", "Signal Voice", "Signal Video", "WhatsApp Message", "WhatsApp Voice", "WhatsApp Video"),
            r.secondary.map { it.label },
        )
        r.secondary[4].perform()
        assertEquals(ContactIntentSpec(Intent.ACTION_VIEW, "content://com.android.contacts/data/3", SIGNAL_VIDEO), factory.launched.single())
    }

    @Test
    fun an_app_button_opens_in_the_app_that_owns_the_row_so_no_other_viewer_is_offered() {
        val factory = RecordingFactory()
        val r = search(listOf(ContactRow(1, NAME, SIGNAL_MSG, "+15550100", 1, appName = "Signal", appPackage = "org.thoughtcrime.securesms", text = "Message +15550100")), factory).single()
        r.secondary.single().perform()
        assertEquals(ContactIntentSpec(Intent.ACTION_VIEW, "content://com.android.contacts/data/1", SIGNAL_MSG, "org.thoughtcrime.securesms"), factory.launched.single())
    }

    @Test
    fun meets_video_and_audio_rows_are_told_apart() {
        val meet = "vnd.android.cursor.item/com.google.android.apps.tachyon.phone.meet"
        val r = search(
            listOf(
                app(1, meet, "Meet", null, 1, "+15550100"),
                app(1, "$meet.audio", "Meet", null, 2, "+15550100"),
            ),
        ).single()
        assertEquals(listOf("Meet", "Meet Audio"), r.secondary.map { it.label })
    }

    @Test
    fun the_same_app_row_on_two_raw_contacts_is_one_button() {
        val r = search(
            listOf(
                app(1, SIGNAL_MSG, "Signal", "Message +15550100", 1),
                app(1, SIGNAL_MSG, "Signal", "Message +15550100", 9),
            ),
        ).single()
        assertEquals(listOf("Signal Message"), r.secondary.map { it.label })
    }

    @Test
    fun a_contact_with_only_connected_rows_still_appears_with_its_buttons() {
        val r = search(listOf(app(1, SIGNAL_VOICE, "Signal", "Signal Voice Call +15550100", 2))).single()
        assertEquals(listOf("Signal Voice"), r.secondary.map { it.label })
    }

    @Test
    fun one_app_with_two_numbers_names_each_button_by_its_number() {
        val r = search(
            listOf(
                app(1, SIGNAL_MSG, "Signal", "Message +15550100", 1, "+15550100"),
                app(1, SIGNAL_MSG, "Signal", "Message +15550199", 2, "+15550199"),
                app(1, SIGNAL_VOICE, "Signal", "Signal Voice Call +15550100", 3, "+15550100"),
            ),
        ).single()
        assertEquals(
            listOf("Signal Message +15550100", "Signal Message +15550199", "Signal Voice"),
            r.secondary.map { it.label },
        )
    }

    @Test
    fun an_app_row_with_no_readable_text_is_named_by_the_app_alone() {
        val r = search(listOf(app(1, SIGNAL_MSG, "Signal", null, 1))).single()
        assertEquals(listOf("Signal"), r.secondary.map { it.label })
    }

    @Test
    fun app_buttons_have_stable_keys_and_whatsapp_keeps_its_old_one() {
        val keys = search(signalAndWhatsApp).single().secondary.map { it.recentId }
        assertEquals("contact:1|app|$SIGNAL_VOICE|+15550100", keys[3])
        assertEquals("contact:1|whatsapp|15550100@s.whatsapp.net", keys[5])
        assertEquals("contact:1|app|$WA_VOICE|15550100@s.whatsapp.net", keys[6])
    }

    @Test
    fun duplicate_phone_rows_give_one_call_and_one_message_button() {
        val r = search(
            listOf(
                ContactRow(1, NAME, PHONE, "+1 555 0100"),
                ContactRow(1, NAME, PHONE, "+15550100"),
                ContactRow(1, NAME, PHONE, "(555) 0199"),
                ContactRow(1, NAME, EMAIL, "alex@example.com"),
                ContactRow(1, NAME, EMAIL, "ALEX@example.com"),
            ),
        ).single()
        // Two different numbers: each is named by its value, since neither has a kind.
        assertEquals(listOf("Call +1 555 0100", "Message +1 555 0100", "Call (555) 0199", "Message (555) 0199", "Email"), r.secondary.map { it.label })
    }

    @Test
    fun several_numbers_and_addresses_are_named_by_their_kind() {
        val r = search(
            listOf(
                ContactRow(1, NAME, PHONE, "+15550100", typeLabel = "Mobile"),
                ContactRow(1, NAME, PHONE, "+15550199", typeLabel = "Work"),
                ContactRow(1, NAME, EMAIL, "a@home.example", typeLabel = "Home"),
                ContactRow(1, NAME, EMAIL, "a@work.example", typeLabel = "Work"),
                ContactRow(1, NAME, EMAIL, "a@other.example", typeLabel = "Other"),
            ),
        ).single()
        assertEquals(
            listOf("Call mobile", "Message mobile", "Call work", "Message work", "Email home", "Email work", "Email other"),
            r.secondary.map { it.label },
        )
    }

    @Test
    fun when_kinds_repeat_the_value_names_the_action_instead() {
        val r = search(
            listOf(
                ContactRow(1, NAME, EMAIL, "one@example.com", typeLabel = "Work"),
                ContactRow(1, NAME, EMAIL, "two@example.com", typeLabel = "Work"),
            ),
        ).single()
        assertEquals(listOf("Email one@example.com", "Email two@example.com"), r.secondary.map { it.label })
    }

    @Test
    fun a_single_number_or_address_keeps_the_plain_label_whatever_its_kind() {
        val r = search(
            listOf(
                ContactRow(1, NAME, PHONE, "+15550100", typeLabel = "Mobile"),
                ContactRow(1, NAME, EMAIL, "a@example.com", typeLabel = "Work"),
            ),
        ).single()
        assertEquals(listOf("Call", "Message", "Email"), r.secondary.map { it.label })
    }

    @Test
    fun a_number_is_encoded_into_the_uri_keeping_plus_and_hiding_hash_and_spaces() {
        val factory = RecordingFactory()
        val r = search(listOf(ContactRow(1, NAME, PHONE, "+1 555#0100")), factory).single()
        r.secondary.forEach { it.perform() }
        assertEquals(listOf("tel:+1%20555%230100", "smsto:+1%20555%230100"), factory.launched.map { it.uri })
    }

    private fun resolve(
        rows: List<ContactRow>,
        ids: Set<String>,
        factory: ContactActionFactory = RecordingFactory(),
        reader: FakeReader = FakeReader(rows),
        access: Boolean = true,
    ) = runBlocking { ContactSource(reader, { access }, EnglishSearchStrings, actionFactory = factory).resolve(ids) }

    private val matt = listOf(
        ContactRow(7, MATT, PHONE, "+15550100"),
        ContactRow(7, MATT, EMAIL, "matt@example.com"),
    )

    @Test
    fun resolve_contact_by_id() {
        val factory = RecordingFactory()
        val r = resolve(matt + ContactRow(8, NAME, PHONE, "+15550199"), setOf("contact:7"), factory).single()
        assertEquals("contact:7", r.id)
        assertEquals(MATT, r.label)
        assertEquals(Kind.Contact, r.kind)
        assertEquals("Contacts", r.sourceName)
        // A recents row is a plain tap target: never folded, no ways to reach the contact listed under it.
        assertTrue(r.secondary.isEmpty())
        assertEquals(0, r.folded)
        r.primary.perform()
        assertEquals("content://com.android.contacts/contacts/7", factory.launched.single().uri)
    }

    @Test
    fun resolve_a_method_key_gives_call_matt_lastname() {
        val factory = RecordingFactory()
        val key = "contact:7|call|+15550100"
        val r = resolve(matt, setOf(key), factory).single()
        assertEquals(key, r.id)
        assertEquals("Call: $MATT", r.label)
        assertEquals(Kind.Contact, r.kind)
        assertEquals("contact:7", r.ownerId)
        assertEquals(key, r.primary.recentId)
        assertTrue(r.secondary.isEmpty())
        r.primary.perform()
        assertEquals(ContactIntentSpec(Intent.ACTION_DIAL, "tel:+15550100"), factory.launched.single())
    }

    @Test
    fun resolve_uses_the_typed_label_when_several_numbers() {
        val rows = listOf(
            ContactRow(7, MATT, PHONE, "+15550100", typeLabel = "Mobile"),
            ContactRow(7, MATT, PHONE, "+15550199", typeLabel = "Work"),
        )
        val r = resolve(rows, linkedSetOf("contact:7|call|+15550100", "contact:7|message|+15550199"))
        assertEquals(listOf("Call mobile: $MATT", "Message work: $MATT"), r.map { it.label })
    }

    @Test
    fun resolve_an_app_key_gives_that_exact_action_never_a_sibling() {
        val factory = RecordingFactory()
        val voice = "contact:1|app|$SIGNAL_VOICE|+15550100"
        val r = resolve(signalAndWhatsApp, linkedSetOf(voice, "contact:1|app|$SIGNAL_MSG|+15550100"), factory)
        assertEquals(listOf("Signal Voice: $NAME", "Signal Message: $NAME"), r.map { it.label })
        assertEquals(voice, r.first().primary.recentId)
        r.first().primary.perform()
        assertEquals(ContactIntentSpec(Intent.ACTION_VIEW, "content://com.android.contacts/data/2", SIGNAL_VOICE), factory.launched.single())
    }

    @Test
    fun resolve_the_old_whatsapp_key_still_works() {
        val r = resolve(signalAndWhatsApp, setOf("contact:1|whatsapp|15550100@s.whatsapp.net")).single()
        assertEquals("WhatsApp Message: $NAME", r.label)
    }

    @Test
    fun resolve_drops_an_app_action_whose_row_is_gone() {
        val rows = signalAndWhatsApp.filter { it.mimetype != SIGNAL_VOICE }
        assertTrue(resolve(rows, setOf("contact:1|app|$SIGNAL_VOICE|+15550100")).isEmpty())
    }

    @Test
    fun resolve_drops_a_removed_number() {
        // No fallback to the contact itself: the pick was that number.
        assertTrue(resolve(matt, setOf("contact:7|call|+15550000", "contact:7|email|old@example.com")).isEmpty())
    }

    @Test
    fun resolve_is_empty_without_contacts_access() {
        val reader = FakeReader(matt)
        assertTrue(resolve(matt, setOf("contact:7", "contact:7|call|+15550100"), reader = reader, access = false).isEmpty())
        assertTrue(reader.contactQueries.isEmpty())
    }

    @Test
    fun resolve_matches_a_key_whose_row_is_no_longer_the_one_kept() {
        // The key was made when "+15550100" came first; now the provider lists the same number differently first.
        val factory = RecordingFactory()
        val key = "contact:7|call|+15550100"
        val rows = listOf(ContactRow(7, MATT, PHONE, "+1 555 0100"), ContactRow(7, MATT, PHONE, "+15550100"))
        val r = resolve(rows, setOf(key), factory).single()
        assertEquals(key, r.id)
        assertEquals("Call: $MATT", r.label)
        assertEquals(key, r.primary.recentId)
        r.primary.perform()
        assertEquals("tel:+1%20555%200100", factory.launched.single().uri)
    }

    @Test
    fun resolve_reads_the_provider_once_for_all_contacts() {
        val reader = FakeReader(
            matt + listOf(
                ContactRow(8, NAME, EMAIL, "alex@example.com"),
                ContactRow(8, NAME, WA, "15550100@s.whatsapp.net", rowId = 42, appName = "WhatsApp", text = "Message +15550100"),
            ),
        )
        val ids = linkedSetOf(
            "contact:7", "contact:7|call|+15550100", "contact:8|email|alex@example.com",
            "contact:8|whatsapp|15550100@s.whatsapp.net", "contact:9", "contact:abc", "app:x/.Main",
        )
        val r = resolve(reader.rows, ids, reader = reader)
        // Several keys name one contact; the gone contact 9 and the malformed and foreign ids are absent.
        assertEquals(listOf(setOf(7L, 8L, 9L)), reader.contactQueries)
        assertEquals(
            listOf("contact:7", "contact:7|call|+15550100", "contact:8|email|alex@example.com", "contact:8|whatsapp|15550100@s.whatsapp.net"),
            r.map { it.id },
        )
        assertEquals(listOf(MATT, "Call: $MATT", "Email: $NAME", "WhatsApp Message: $NAME"), r.map { it.label })
    }

    @Test
    fun resolve_reads_the_provider_off_the_calling_thread() {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "contacts-thread") }
        try {
            val reader = FakeReader(matt)
            val source = ContactSource(reader, { true }, EnglishSearchStrings, executor.asCoroutineDispatcher(), RecordingFactory())
            assertEquals(1, runBlocking { source.resolve(setOf("contact:7")) }.size)
            assertTrue(reader.threads.single().name == "contacts-thread")
        } finally {
            executor.shutdown()
        }
    }
}
