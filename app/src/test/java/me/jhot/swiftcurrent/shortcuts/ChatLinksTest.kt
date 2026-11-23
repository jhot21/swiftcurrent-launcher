package me.jhot.swiftcurrent.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatLinksTest {
    private fun uri(text: String) = ChatLinks.parse(text)?.link?.uri

    @Test
    fun every_form_of_a_space_link_opens_the_room_deep_link() {
        val want = "https://chat.google.com/room/AAAA3Qj4PVo"
        assertEquals(want, uri("https://mail.google.com/mail/u/0/#chat/space/AAAA3Qj4PVo"))
        assertEquals(want, uri("https://mail.google.com/chat/u/1/#chat/space/AAAA3Qj4PVo"))
        assertEquals(want, uri("https://chat.google.com/app/chat/AAAA3Qj4PVo"))
        assertEquals(want, uri("https://chat.google.com/room/AAAA3Qj4PVo?cls=7"))
        assertEquals(want, uri("chat.google.com/room/AAAA3Qj4PVo"))
    }

    @Test
    fun every_form_of_a_dm_link_opens_the_dm_deep_link() {
        val want = "https://chat.google.com/dm/3xv1HgAAAAE"
        assertEquals(want, uri("https://mail.google.com/mail/u/0/#chat/dm/3xv1HgAAAAE"))
        assertEquals(want, uri("https://chat.google.com/app/chat/3xv1HgAAAAE"))
        assertEquals(want, uri("https://chat.google.com/dm/3xv1HgAAAAE"))
    }

    @Test
    fun the_id_is_returned_for_looking_up_the_conversations_name() {
        assertEquals("AAAA3Qj4PVo", ChatLinks.parse("https://chat.google.com/app/chat/AAAA3Qj4PVo")?.id)
    }

    @Test
    fun anything_else_is_refused() {
        assertNull(uri("https://example.com/room/AAAA3Qj4PVo"))
        assertNull(uri("https://mail.google.com/mail/u/0/#inbox"))
        assertNull(uri("https://chat.google.com/"))
        assertNull(uri("https://chat.google.com/room/short"))
        assertNull(uri("javascript:alert(1)"))
        assertNull(uri("not a link"))
    }
}
