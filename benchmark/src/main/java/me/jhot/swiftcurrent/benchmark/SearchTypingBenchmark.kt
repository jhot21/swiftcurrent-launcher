package me.jhot.swiftcurrent.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * Frame timing while typing a query into search. Each measured block runs several open / type /
 * close cycles so there are enough frames to rank.
 */
class SearchTypingBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun typeQueries() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), FrameFloorMetric(minFrames = 30)),
        compilationMode = steadyState,
        startupMode = StartupMode.WARM,
        iterations = benchIterations,
        setupBlock = { startHomeReady() },
    ) {
        repeat(CYCLES) {
            openSearch()
            QUERY.forEach { device.executeShellCommand("input text $it") }
            device.waitForIdle()
            closeSearch()
        }
    }

    private companion object {
        const val CYCLES = 3
        const val QUERY = "matt"
    }
}
