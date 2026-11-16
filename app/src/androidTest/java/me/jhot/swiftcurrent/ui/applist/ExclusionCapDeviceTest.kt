package me.jhot.swiftcurrent.ui.applist

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import me.jhot.swiftcurrent.EmulatorOnly
import me.jhot.swiftcurrent.HomeActivity
import me.jhot.swiftcurrent.ui.edge.ScrubberExclusion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Asks the window for far more excluded height than allowed and checks what the window manager keeps (observed: 200dp per edge, measured in a dumpsys window region). */
class ExclusionCapDeviceTest {
    @get:Rule
    val emulatorOnly = EmulatorOnly

    @Test
    fun platform_keeps_at_most_the_cap_of_excluded_height() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var view: View
            scenario.onActivity { activity ->
                view = View(activity)
                activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            val w = view.resources.displayMetrics.widthPixels
            val capPx = ExclusionCap.capPx(instrumentation.targetContext.resources.displayMetrics.density)
            for (h in listOf(300, 2000)) {
                scenario.onActivity { view.systemGestureExclusionRects = listOf(Rect(w - 200, 600, w, 600 + h)) }
                Thread.sleep(1700)
                val d = instrumentation.uiAutomation.executeShellCommand("dumpsys window").use { fd ->
                    java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText()
                }
                val region = d.lines().first { it.contains("mSystemGestureExclusion") }.trim()
                println("OBSERVED_CAP requested=${h}px -> $region")
                // Only the band touching the screen edge counts toward the cap; the rest of the rect is clipped off it.
                val edgeHeight = Regex("""\((\d+),(\d+),(\d+),(\d+)\)""").findAll(region)
                    .map { it.groupValues.drop(1).map(String::toInt) }
                    .filter { it[2] == w }.sumOf { it[3] - it[1] }
                assertEquals("edge exclusion kept for a $h px request", minOf(h, capPx), edgeHeight)
            }
        }
    }
}

private object ExclusionCap {
    fun capPx(density: Float) = (ScrubberExclusion.CAP_DP * density).toInt()
}
