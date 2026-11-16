package me.jhot.swiftcurrent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvalTest {
    private val a = "AA:BB:CC:DD:EE:01"
    private val b = "AA:BB:CC:DD:EE:02"

    private fun entry(vararg c: Condition) = Entry("e", Target.Shortcut("s"), conditions = c.toList())
    private fun meld(mode: String, on: Boolean = true) = Condition.MeldMode(mode, on)
    private fun bt(address: String?, on: Boolean = true) = Condition.Bluetooth(address, "Dev", on)

    @Test
    fun no_conditions_always_shown() {
        assertTrue(ConditionEval.shown(entry(), ContextState()))
    }

    @Test
    fun meld_active_matches_and_inactive_is_its_opposite() {
        val s = ContextState(activeModes = setOf("Work"), knownModes = setOf("Work", "Home"))
        assertTrue(ConditionEval.holds(meld("Work"), s))
        assertFalse(ConditionEval.holds(meld("Work", false), s))
        assertFalse(ConditionEval.holds(meld("Home"), s))
        assertTrue(ConditionEval.holds(meld("Home", false), s))
    }

    @Test
    fun meld_unknown_hides_in_both_polarities() {
        val s = ContextState(activeModes = null)
        assertFalse(ConditionEval.holds(meld("Work"), s))
        assertFalse(ConditionEval.holds(meld("Work", false), s))
    }

    @Test
    fun meld_mode_missing_from_known_modes_is_broken_and_never_matches() {
        val s = ContextState(activeModes = emptySet(), knownModes = setOf("Home"))
        assertTrue(ConditionEval.isBroken(meld("Work"), s))
        assertFalse(ConditionEval.holds(meld("Work"), s))
        assertFalse(ConditionEval.holds(meld("Work", false), s))
    }

    @Test
    fun meld_known_modes_null_is_not_broken_but_unknown_if_active_null() {
        val s = ContextState(activeModes = null, knownModes = null)
        assertFalse(ConditionEval.isBroken(meld("Work"), s))
        assertFalse(ConditionEval.holds(meld("Work"), s))
        assertTrue(ConditionEval.holds(meld("Work", false), ContextState(activeModes = emptySet())))
    }

    @Test
    fun bluetooth_device_connected_and_not_connected() {
        val s = ContextState(connectedBluetooth = setOf(a), pairedBluetooth = setOf(a, b))
        assertTrue(ConditionEval.holds(bt(a), s))
        assertFalse(ConditionEval.holds(bt(a, false), s))
        assertFalse(ConditionEval.holds(bt(b), s))
        assertTrue(ConditionEval.holds(bt(b, false), s))
    }

    @Test
    fun bluetooth_any_connected_means_at_least_one() {
        assertTrue(ConditionEval.holds(bt(null), ContextState(connectedBluetooth = setOf(b))))
        assertFalse(ConditionEval.holds(bt(null), ContextState(connectedBluetooth = emptySet())))
    }

    @Test
    fun bluetooth_any_not_connected_means_none() {
        assertTrue(ConditionEval.holds(bt(null, false), ContextState(connectedBluetooth = emptySet())))
        assertFalse(ConditionEval.holds(bt(null, false), ContextState(connectedBluetooth = setOf(a))))
    }

    @Test
    fun adapter_off_with_not_connected_condition_on_a_specific_device_shows_the_favorite() {
        // While the adapter is off the monitor keeps the last-known paired set and publishes nothing connected.
        val s = ContextState(connectedBluetooth = emptySet(), pairedBluetooth = setOf(a, b))
        assertFalse(ConditionEval.isBroken(bt(a, false), s))
        assertTrue(ConditionEval.shown(entry(bt(a, false)), s))
        assertFalse(ConditionEval.shown(entry(bt(a)), s))
    }

    @Test
    fun bluetooth_adapter_off_is_empty_set_so_not_connected_shows_and_connected_hides() {
        val s = ContextState(connectedBluetooth = emptySet(), pairedBluetooth = null)
        assertTrue(ConditionEval.holds(bt(a, false), s))
        assertFalse(ConditionEval.holds(bt(a), s))
        assertFalse(ConditionEval.isBroken(bt(a), s))
    }

    @Test
    fun bluetooth_unpaired_address_is_broken() {
        val s = ContextState(connectedBluetooth = emptySet(), pairedBluetooth = setOf(b))
        assertTrue(ConditionEval.isBroken(bt(a), s))
        assertFalse(ConditionEval.holds(bt(a), s))
        assertFalse(ConditionEval.holds(bt(a, false), s))
        assertFalse(ConditionEval.isBroken(bt(null), s))
    }

    @Test
    fun bluetooth_connected_null_hides_both_polarities() {
        val s = ContextState(connectedBluetooth = null)
        assertFalse(ConditionEval.holds(bt(a), s))
        assertFalse(ConditionEval.holds(bt(a, false), s))
        assertFalse(ConditionEval.holds(bt(null), s))
        assertFalse(ConditionEval.holds(bt(null, false), s))
    }

    @Test
    fun all_conditions_must_hold() {
        val s = ContextState(connectedBluetooth = setOf(a), activeModes = setOf("Work"))
        assertTrue(ConditionEval.shown(entry(bt(a), meld("Work")), s))
        assertFalse(ConditionEval.shown(entry(bt(a), meld("Home")), s))
    }

    @Test
    fun keepingLastKnown_fills_only_null_fields() {
        val prev = ContextState(setOf(a), setOf(a, b), setOf("Work"), setOf("Work", "Home"))
        val next = ContextState(connectedBluetooth = emptySet(), activeModes = null)
        assertEquals(
            ContextState(emptySet(), setOf(a, b), setOf("Work"), setOf("Work", "Home")),
            next.keepingLastKnown(prev),
        )
    }
}
