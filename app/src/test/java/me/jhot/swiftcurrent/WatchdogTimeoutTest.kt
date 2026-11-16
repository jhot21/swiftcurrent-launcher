package me.jhot.swiftcurrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class WatchdogTimeoutTest {
    private val description = Description.createTestDescription("Hang", "forever")

    @Test
    fun a_test_that_never_finishes_fails_instead_of_hanging() {
        val hang = object : Statement() {
            override fun evaluate() {
                Thread.sleep(Long.MAX_VALUE)
            }
        }
        try {
            WatchdogTimeout(seconds = 1).apply(hang, description).evaluate()
            fail("expected the watchdog to fail the test")
        } catch (e: AssertionError) {
            assertTrue(e.message, e.message!!.contains("did not finish within 1s"))
        }
    }

    @Test
    fun a_test_that_finishes_passes_through_untouched_and_a_failure_keeps_its_own_type() {
        var ran = 0
        WatchdogTimeout(seconds = 30).apply(object : Statement() { override fun evaluate() { ran++ } }, description).evaluate()
        assertEquals(1, ran)
        try {
            WatchdogTimeout(seconds = 30).apply(object : Statement() { override fun evaluate(): Unit = throw IllegalStateException("own") }, description).evaluate()
            fail("expected the test's own exception")
        } catch (e: IllegalStateException) {
            assertEquals("own", e.message)
        }
    }
}
