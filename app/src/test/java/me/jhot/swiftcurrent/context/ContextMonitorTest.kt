package me.jhot.swiftcurrent.context

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.jhot.swiftcurrent.core.ContextState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContextMonitorTest {
    private val a = "AA:BB:CC:DD:EE:01"
    private val b = "AA:BB:CC:DD:EE:02"

    private class FakeMeld : MeldModes {
        @Volatile var modes: List<String>? = null

        @Volatile var active: List<String>? = null

        @Volatile var fails = false
        val activeReads = java.util.concurrent.atomic.AtomicInteger()
        override fun modes(): List<String>? = if (fails) throw SecurityException("denied") else modes
        override fun active(): List<String>? {
            activeReads.incrementAndGet()
            return if (fails) throw SecurityException("denied") else active
        }
    }

    private class FakeBluetooth : BluetoothDevices {
        @Volatile var paired: List<PairedDevice>? = null

        @Volatile var connected: Set<String>? = null

        @Volatile var fails = false
        override fun paired(): List<PairedDevice>? = if (fails) throw IllegalStateException("binder died") else paired
        override fun connected(): Set<String>? = if (fails) throw IllegalStateException("binder died") else connected
    }

    private val meld = FakeMeld()
    private val bluetooth = FakeBluetooth()

    private fun TestScope.monitor() =
        ContextMonitor(meld, bluetooth, StandardTestDispatcher(testScheduler), this)

    private fun TestScope.refreshed(monitor: ContextMonitor): ContextState {
        monitor.refresh()
        advanceUntilIdle()
        return monitor.state.value
    }

    @Test
    fun refresh_publishes_what_the_readers_return() = runTest {
        meld.modes = listOf("Home", "Work")
        meld.active = listOf("Work")
        bluetooth.paired = listOf(PairedDevice(a, "Earbuds"), PairedDevice(b, "Car"))
        bluetooth.connected = setOf(a)

        assertEquals(
            ContextState(
                connectedBluetooth = setOf(a),
                pairedBluetooth = setOf(a, b),
                activeModes = setOf("Work"),
                knownModes = setOf("Home", "Work"),
            ),
            refreshed(monitor()),
        )
    }

    @Test
    fun a_null_read_keeps_the_last_known_value() = runTest {
        val monitor = monitor()
        meld.modes = listOf("Work")
        meld.active = listOf("Work")
        bluetooth.paired = listOf(PairedDevice(a, "Earbuds"))
        bluetooth.connected = setOf(a)
        val known = refreshed(monitor)

        meld.modes = null
        meld.active = null
        bluetooth.paired = null
        bluetooth.connected = null

        assertEquals(known, refreshed(monitor))
    }

    @Test
    fun first_read_null_leaves_the_field_null() = runTest {
        bluetooth.connected = setOf(a)

        val state = refreshed(monitor())

        assertEquals(setOf(a), state.connectedBluetooth)
        assertNull(state.pairedBluetooth)
        assertNull(state.activeModes)
        assertNull(state.knownModes)
    }

    @Test
    fun meld_active_is_never_defaulted_to_empty_on_null() = runTest {
        val monitor = monitor()
        meld.active = listOf("Work")
        refreshed(monitor)

        meld.active = null
        assertEquals(setOf("Work"), refreshed(monitor).activeModes)

        // A real empty answer is different: no modes in effect.
        meld.active = emptyList()
        assertEquals(emptySet<String>(), refreshed(monitor).activeModes)
    }

    @Test
    fun adapter_off_publishes_an_empty_connected_set_and_keeps_last_known_paired() = runTest {
        val monitor = monitor()
        bluetooth.paired = listOf(PairedDevice(a, "Earbuds"), PairedDevice(b, "Car"))
        bluetooth.connected = setOf(a)
        refreshed(monitor)

        bluetooth.paired = null
        bluetooth.connected = emptySet()
        val state = refreshed(monitor)

        assertEquals(emptySet<String>(), state.connectedBluetooth)
        assertEquals(setOf(a, b), state.pairedBluetooth)
    }

    @Test
    fun a_reader_that_throws_is_treated_as_unknown_and_the_other_reader_still_updates() = runTest {
        val monitor = monitor()
        meld.active = listOf("Work")
        bluetooth.connected = setOf(a)
        refreshed(monitor)

        meld.fails = true
        bluetooth.connected = setOf(b)
        val state = refreshed(monitor)

        assertEquals(setOf("Work"), state.activeModes)
        assertEquals(setOf(b), state.connectedBluetooth)

        // The monitor survived the failure and still reads.
        meld.fails = false
        meld.active = listOf("Home")
        assertEquals(setOf("Home"), refreshed(monitor).activeModes)
    }

    @Test
    fun a_burst_of_refreshes_before_the_read_starts_collapses_into_one_read() = runTest {
        val monitor = monitor()
        meld.active = listOf("Work")

        repeat(5) { monitor.refresh() }
        advanceUntilIdle()

        assertEquals(1, meld.activeReads.get())
        assertEquals(setOf("Work"), monitor.state.value.activeModes)

        // Once that read has run, the next nudge reads again.
        refreshed(monitor)
        assertEquals(2, meld.activeReads.get())
    }
}
