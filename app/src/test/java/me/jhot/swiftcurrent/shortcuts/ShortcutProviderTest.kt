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
}
