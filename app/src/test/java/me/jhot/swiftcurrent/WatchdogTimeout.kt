package me.jhot.swiftcurrent

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A timeout that does not move the test to another thread. JUnit's own `Timeout` rule does, and
 * Robolectric then refuses ("main looper can only be controlled from main thread"). This one watches
 * from a daemon thread and interrupts the test thread when [seconds] pass, so a hang (an animation
 * that never settles) fails with a message instead of stalling the whole gate. If the interrupt is
 * ignored the worker is halted after a grace period: a crashed run is still a failed run, not a hang.
 */
class WatchdogTimeout(private val seconds: Long = 60, private val graceSeconds: Long = 15) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val testThread = Thread.currentThread()
            val done = AtomicBoolean(false)
            val fired = AtomicBoolean(false)
            val dog = Thread {
                try {
                    Thread.sleep(seconds * 1000)
                    if (done.get()) return@Thread
                    fired.set(true)
                    System.err.println("WatchdogTimeout: ${description.displayName} still running after ${seconds}s")
                    testThread.stackTrace.forEach { System.err.println("    at $it") }
                    testThread.interrupt()
                    Thread.sleep(graceSeconds * 1000)
                    if (!done.get()) Runtime.getRuntime().halt(2)
                } catch (_: InterruptedException) {
                    // The test finished first.
                }
            }.apply { isDaemon = true; name = "watchdog-${description.methodName}"; start() }
            try {
                base.evaluate()
            } catch (t: Throwable) {
                if (fired.get()) throw AssertionError("${description.displayName} did not finish within ${seconds}s", t)
                throw t
            } finally {
                done.set(true)
                dog.interrupt()
                Thread.interrupted()
            }
        }
    }
}
