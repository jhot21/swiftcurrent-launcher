package me.jhot.swiftcurrent.search.sources

import me.jhot.swiftcurrent.search.sources.ContactActionKey.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ContactActionKeyTest {
    @Test
    fun round_trips_each_method() {
        val data = mapOf(
            Method.Call to "+1 555-0100",
            Method.Message to "+1 555-0100",
            Method.Email to "alex@example.com",
        )
        for ((method, value) in data) {
            val key = ContactActionKey.of(7, method, value)
            assertEquals("contact:7|${method.name.lowercase()}|$value", key)
            assertEquals(ContactActionKey.Parsed(7, method, value), ContactActionKey.parse(key))
        }
    }

    @Test
    fun app_keys_round_trip_with_the_mimetype_and_data() {
        val mimetype = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
        for (data in listOf("+1 555-0100", "a|b|c")) {
            val key = ContactActionKey.ofApp(7, mimetype, data)
            assertEquals("contact:7|app|$mimetype|$data", key)
            assertEquals(ContactActionKey.Parsed(7, Method.App, data, mimetype), ContactActionKey.parse(key))
        }
    }

    @Test
    fun whatsapp_keeps_the_key_it_had_before_it_became_a_generic_app() {
        val wa = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
        val key = ContactActionKey.ofApp(7, wa, "15550100@s.whatsapp.net")
        assertEquals("contact:7|whatsapp|15550100@s.whatsapp.net", key)
        assertEquals(ContactActionKey.Parsed(7, Method.App, "15550100@s.whatsapp.net", wa), ContactActionKey.parse(key))
    }

    @Test
    fun an_app_key_cannot_be_built_without_a_mimetype() {
        assertThrows(IllegalArgumentException::class.java) { ContactActionKey.of(7, Method.App, "x") }
    }

    @Test
    fun parse_rejects_malformed_app_keys() {
        for (bad in listOf("contact:7|app|onlymimetype", "contact:7|app||x", "contact:7|app|m|")) {
            assertNull(bad, ContactActionKey.parse(bad))
        }
    }

    @Test
    fun parse_rejects_other_ids() {
        for (other in listOf(
            "contact:7",
            "app:pkg/.Main",
            "shortcut:pkg/id",
            "contact:x|call|+15550100",
            "contact:7|fax|+15550100",
            "contact:7|Call|+15550100",
            "contact:7|call",
            "contact:7|call|",
            "",
        )) {
            assertNull(other, ContactActionKey.parse(other))
        }
    }
}
