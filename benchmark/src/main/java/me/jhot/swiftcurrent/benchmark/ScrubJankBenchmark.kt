package me.jhot.swiftcurrent.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/** Dropped frames while scrubbing the alphabet up and down and flinging the list. */
class ScrubJankBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrubAndFling() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), FrameFloorMetric(minFrames = 30)),
        compilationMode = steadyState,
        startupMode = StartupMode.WARM,
        iterations = benchIterations,
        setupBlock = {
            startHomeReady()
            openList() // throws if the swipe missed, so a miss cannot masquerade as a scrub result
        },
    ) {
        val h = device.displayHeight
        val x = device.stripX()
        // Down the alphabet and back up, one continuous touch each way.
        // Both ends stay on the track: lifting above the star would close the list.
        device.swipe(x, (h * TRACK_TOP).toInt(), x, (h * TRACK_BOTTOM).toInt(), 120)
        device.swipe(x, (h * TRACK_BOTTOM).toInt(), x, (h * TRACK_TOP).toInt(), 120)
        // Fling the list body, away from the strip so it is not read as a scrub.
        val lx = (device.displayWidth * 0.4f).toInt()
        repeat(3) { device.swipe(lx, (h * 0.8f).toInt(), lx, (h * 0.2f).toInt(), 8) }
        repeat(3) { device.swipe(lx, (h * 0.2f).toInt(), lx, (h * 0.8f).toInt(), 8) }
        device.waitForIdle()
    }
}
