package me.jhot.swiftcurrent.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutProviderTest {
    private fun entry(short: CharSequence?, long: CharSequence? = null, enabled: Boolean = true) =
        shortcutEntryOrNull("Tasker", "pkg", "id", short, long, enabled)

    @Test
    fun null_blank_or_disabled_shortcuts_are_skipped() {
        assertNull(entry(null))
        assertNull(entry(""))
        assertNull(entry("   ", "\t"))
        assertNull(entry("Bedtime", enabled = false))
        assertEquals("Bedtime", entry("Bedtime")?.label)
        assertEquals("pkg/id", entry("Bedtime")?.key)
    }

    @Test
    fun short_label_is_preferred_and_long_label_is_the_fallback() {
        assertEquals("Short", entry("Short", "A much longer label")?.label)
        assertEquals("Long", entry(" ", "Long")?.label)
    }

    @Test
    fun pinned_flag_is_carried_onto_the_entry() {
        assertEquals(true, shortcutEntryOrNull("Tasker", "pkg", "id", "Bedtime", null, true, pinned = true)?.pinned)
        assertEquals(false, entry("Bedtime")?.pinned)
    }

    @Test
    fun pinsAfterRemoving_drops_only_the_given_id_and_keeps_order() {
        assertEquals(listOf("a", "c"), pinsAfterRemoving(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("a", "b"), pinsAfterRemoving(listOf("a", "b"), "z"))
        assertEquals(emptyList<String>(), pinsAfterRemoving(emptyList(), "a"))
    }

    @Test
    fun gmail_chat_conversations_are_filed_under_google_chat() {
        val room = setOf("android.shortcut.conversation.room")
        val dm = setOf("android.shortcut.conversation.dm")
        assertEquals("Google Chat", appLabelFor("Gmail", "com.google.android.gm", room))
        assertEquals("Google Chat", appLabelFor("Gmail", "com.google.android.gm", dm))
        // Gmail's own shortcuts (compose a message to a person) stay Gmail.
        assertEquals("Gmail", appLabelFor("Gmail", "com.google.android.gm", setOf("actions.intent.SEND_MESSAGE")))
        assertEquals("Gmail", appLabelFor("Gmail", "com.google.android.gm", emptySet()))
        // Another app's conversations keep that app's name.
        assertEquals("Signal", appLabelFor("Signal", "org.thoughtcrime.securesms", room))
    }
}
