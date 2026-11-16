package me.jhot.swiftcurrent.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * Frame timing over repeated edge-swipe opens of the app list. One open is only a handful of
 * frames, so each measured block runs several open/close cycles to collect enough to rank.
 */
class AppListOpenBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun openAppList() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), FrameFloorMetric(minFrames = 30)),
        compilationMode = steadyState,
        startupMode = StartupMode.WARM,
        iterations = benchIterations,
        // startHomeReady closes a list the previous iteration left open.
        setupBlock = { startHomeReady() },
    ) {
        repeat(OPEN_CYCLES) {
            openList()
            closeList()
        }
    }

    private companion object {
        const val OPEN_CYCLES = 5
    }
}
