package me.jhot.swiftcurrent

import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Tests that change the default home app must never run on a real phone; they skip there. */
object EmulatorOnly : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            assumeTrue("Skipped: not an emulator", isRunningOnEmulator())
            base.evaluate()
        }
    }
}
