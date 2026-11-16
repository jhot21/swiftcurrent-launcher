package me.jhot.swiftcurrent.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OrElseTest {
    @Test
    fun answers_the_block_when_it_succeeds() {
        assertEquals(1, orElse(0) { 1 })
    }

    @Test
    fun answers_the_fallback_when_the_block_throws() {
        assertEquals(0, orElse(0) { error("boom") })
    }

    @Test
    fun lets_cancellation_through() {
        try {
            orElse(0) { throw CancellationException("cancelled") }
            fail("cancellation was swallowed")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun the_block_may_suspend() = runBlocking {
        assertEquals(2, orElse(0) { yield(); 2 })
    }
}
