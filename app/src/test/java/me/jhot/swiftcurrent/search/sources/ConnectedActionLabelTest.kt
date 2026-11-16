package me.jhot.swiftcurrent.search.sources

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedActionLabelTest {
    private fun label(app: String, text: String?, data: String) = connectedActionLabel(app, text, data)

    @Test
    fun signal_message_row_is_named_message() =
        assertEquals("Signal Message", label("Signal", "Message +15550100123", "+15550100123"))

    @Test
    fun signal_video_call_is_shortened_to_video() =
        assertEquals("Signal Video", label("Signal", "Signal Video Call +15550100123", "+15550100123"))

    @Test
    fun signal_voice_call_is_shortened_to_voice() =
        assertEquals("Signal Voice", label("Signal", "Signal Voice Call +15550100123", "+15550100123"))

    @Test
    fun whatsapp_number_in_text_may_differ_from_the_jid() =
        assertEquals("WhatsApp Message", label("WhatsApp", "Message +1 555-010-0123", "15550100123@s.whatsapp.net"))

    @Test
    fun whatsapp_video_and_voice_rows_with_any_number_layout() {
        assertEquals("WhatsApp Video", label("WhatsApp", "Video call +1 (555) 010-0123", "15550100123@s.whatsapp.net"))
        assertEquals("WhatsApp Voice", label("WhatsApp", "Voice call +44 20000 00000", "x"))
    }

    @Test
    fun meet_style_text_without_a_number() =
        assertEquals("Meet Video", label("Meet", "Video call", "x"))

    @Test
    fun text_that_already_names_the_action_and_the_app_is_the_label() {
        assertEquals("Voice call on Meet", label("Meet", "Voice call on Meet", "x"))
        assertEquals("Video call on Meet", label("Meet", "Video call on Meet", "a@example.com"))
    }

    @Test
    fun a_bare_ten_digit_number_is_dashed_like_the_rest_of_the_list() {
        assertEquals("201-213-3571", connectedActionNumber(null, "2012133571"))
        assertEquals("+15550100", connectedActionNumber(null, "+15550100"))
    }

    @Test
    fun no_readable_action_word_gives_the_app_name_alone() {
        assertEquals("Signal", label("Signal", "+15550100123", "+15550100123"))
        assertEquals("Signal", label("Signal", "", "x"))
        assertEquals("Signal", label("Signal", null, "x"))
        assertEquals("Signal", label("Signal", "Signal", "x"))
    }

    @Test
    fun short_data_is_not_stripped_out_of_the_middle_of_a_word() =
        assertEquals("Signal Message", label("Signal", "Message", "a"))

    @Test
    fun the_number_a_row_is_for_comes_from_its_text_else_its_data() {
        assertEquals("+1 (555) 010-0123", connectedActionNumber("Message +1 (555) 010-0123", "15550100123@s.whatsapp.net"))
        assertEquals("+15550100", connectedActionNumber("Message", "+15550100"))
        assertEquals("+15550100", connectedActionNumber(null, "+15550100"))
    }

    private val meet = "vnd.android.cursor.item/com.google.android.apps.tachyon.phone.meet"

    @Test
    fun two_buttons_of_one_app_that_read_alike_are_told_apart_by_their_mimetype() {
        assertEquals(listOf("Meet", "Meet Audio"), distinguishLabels(listOf("Meet", "Meet"), listOf(meet, "$meet.audio")))
    }

    @Test
    fun labels_that_already_differ_are_left_alone() {
        assertEquals(listOf("Signal Message", "Signal Voice"), distinguishLabels(listOf("Signal Message", "Signal Voice"), listOf("a.b", "a.c")))
    }

    @Test
    fun several_alike_labels_each_get_the_part_of_their_mimetype_that_differs() {
        assertEquals(
            listOf("Foo Video", "Foo Voice", "Foo Audio"),
            distinguishLabels(listOf("Foo", "Foo", "Foo"), listOf("x.foo.video", "x.foo.voice", "x.foo.audio")),
        )
    }
}
