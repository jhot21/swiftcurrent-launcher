package me.jhot.swiftcurrent.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/** Cold start to the first frame of HomeActivity. */
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartNoCompilation() = coldStart(CompilationMode.None())

    @Test
    fun coldStartFullCompilation() = coldStart(CompilationMode.Full())

    private fun coldStart(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = benchIterations,
        setupBlock = { pressHome() },
    ) {
        startHome()
        // First run only: the setup page is not the Home screen being measured next time.
        device.wait(Until.findObject(By.text("Skip for now")), 500)?.click()
    }
}
