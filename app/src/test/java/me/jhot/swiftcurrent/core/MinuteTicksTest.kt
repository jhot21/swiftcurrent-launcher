package me.jhot.swiftcurrent.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MinuteTicksTest {
    // Virtual time starts at 0, so the clock reads 30 s into a minute plus whatever the test advances.
    @Test
    fun ticks_at_once_then_just_after_each_minute_boundary() = runTest {
        val seen = mutableListOf<Long>()
        val job = launch { minuteTicks({ 30_000L + currentTime }, emptyFlow()).collect { seen += it } }
        runCurrent()
        assertEquals(listOf(30_000L), seen)

        advanceTimeBy(30_049L)
        assertEquals("not yet", listOf(30_000L), seen)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(30_000L, 60_050L), seen)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(listOf(30_000L, 60_050L, 120_050L), seen)
        job.cancel()
    }

    // Setting the clock or moving zones leaves the old boundary meaningless: read again now.
    @Test
    fun a_clock_or_zone_change_ticks_at_once() = runTest {
        val changes = MutableSharedFlow<Unit>()
        val seen = mutableListOf<Long>()
        val job = launch { minuteTicks({ 30_000L + currentTime }, changes).collect { seen += it } }
        runCurrent()
        advanceTimeBy(5_000L)
        changes.emit(Unit)
        runCurrent()
        assertEquals(listOf(30_000L, 35_000L), seen)
        job.cancel()
    }
}
