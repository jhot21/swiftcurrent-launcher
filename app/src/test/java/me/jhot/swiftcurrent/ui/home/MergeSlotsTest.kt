package me.jhot.swiftcurrent.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeSlotsTest {
    private fun settled(vararg keys: String) = keys.map { Slot(it, it, leaving = false) }

    private fun merge(previous: List<Slot<String>>, vararg items: String) = mergeSlots(previous, items.toList()) { it }

    private fun describe(slots: List<Slot<String>>) = slots.map { if (it.leaving) "${it.key}!" else it.key.toString() }

    @Test
    fun first_merge_has_no_leaving_slots() {
        assertEquals(listOf("a", "b"), describe(merge(emptyList(), "a", "b")))
    }

    @Test
    fun a_newcomer_takes_its_position_in_the_list() {
        assertEquals(listOf("a", "b", "c"), describe(merge(settled("a", "c"), "a", "b", "c")))
    }

    @Test
    fun a_removed_item_stays_leaving_after_its_predecessor() {
        assertEquals(listOf("a", "b!", "c"), describe(merge(settled("a", "b", "c"), "a", "c")))
    }

    @Test
    fun a_removed_first_item_stays_first() {
        assertEquals(listOf("a!", "b"), describe(merge(settled("a", "b"), "b")))
    }

    @Test
    fun an_item_that_returns_is_no_longer_leaving() {
        val leaving = listOf(Slot("a", "a", false), Slot("b", "b", true), Slot("c", "c", false))
        assertEquals(listOf("a", "b", "c"), describe(merge(leaving, "a", "b", "c")))
    }

    @Test
    fun a_still_leaving_slot_is_kept_across_merges() {
        val once = merge(settled("a", "b", "c"), "a", "c")
        assertEquals(listOf("a", "b!", "c"), describe(mergeSlots(once, listOf("a", "c")) { it }))
    }

    @Test
    fun the_newest_item_value_wins() {
        val previous = listOf(Slot("k", "k" to "old", false))
        val merged = mergeSlots(previous, listOf("k" to "new"), { it.first })
        assertEquals("new", merged.single().item.second)
    }
}
