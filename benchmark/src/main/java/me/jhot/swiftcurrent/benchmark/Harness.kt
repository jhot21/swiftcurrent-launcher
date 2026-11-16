package me.jhot.swiftcurrent.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val TARGET_PACKAGE = "me.jhot.swiftcurrent"

/**
 * Iterations per measured benchmark: 10, the number the recorded results use. A quick look can lower it with
 * `task bench -- -Pandroid.testInstrumentationRunnerArguments.bench.iterations=3`; fewer iterations are noisier, so
 * compare only runs made with the same count.
 */
val benchIterations: Int
    get() = InstrumentationRegistry.getArguments().getString("bench.iterations")?.toIntOrNull()?.coerceAtLeast(1) ?: 10

/** HomeActivity only declares the HOME category, so the default launcher-intent lookup finds nothing. */
fun MacrobenchmarkScope.startHome() {
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.HomeActivity"),
    )
}

/**
 * Partial() installs a baseline profile through the target's profileinstaller, which refuses a
 * device newer than it knows (the API 36 emulator). Full is the fully-compiled bound instead.
 */
val steadyState: CompilationMode = CompilationMode.Full()

/** The scrubber and the home edge swipe share the right 15% of the screen. */
fun UiDevice.stripX(fractionFromRight: Float = 0.07f): Int =
    (displayWidth * (1f - fractionFromRight)).toInt()

/**
 * The scrubber's track sits in the lower part of the strip, with the star at its top, and lifting on
 * or above the star closes the list. So a swipe that opens the list must end on a letter: the track
 * runs from about 0.70 to 0.92 of the screen height.
 */
const val TRACK_TOP = 0.74f
const val TRACK_BOTTOM = 0.90f

/** Swipe up inside the right edge strip, the gesture that opens the app list; the finger ends on a letter. */
fun UiDevice.edgeSwipeUp(steps: Int = 30) {
    val x = stripX()
    swipe(x, (displayHeight * TRACK_BOTTOM).toInt(), x, (displayHeight * (TRACK_TOP + 0.04f)).toInt(), steps)
}

/**
 * The app list is the only screen with single-letter text (section headers and the scrubber); Home
 * shows a clock, a date and favorites. Matching on text needs no change to the app's semantics.
 */
private val listMarker = By.text(Pattern.compile("[A-Z#]"))

/** Starts Home, skips the first-run setup page if it is showing, and leaves the app list closed. */
fun MacrobenchmarkScope.startHomeReady() {
    startHome()
    // Without this the measured gestures would land on the setup page, not on Home.
    device.wait(Until.findObject(By.text("Skip for now")), 5_000)?.click()
    device.waitForIdle()
    if (device.hasObject(listMarker)) closeList()
}

/** Opens the app list with the edge swipe and fails loudly if it did not appear. */
fun MacrobenchmarkScope.openList() {
    device.edgeSwipeUp()
    check(device.wait(Until.hasObject(listMarker), 3_000)) { "the app list did not open after the edge swipe" }
}

fun MacrobenchmarkScope.closeList() {
    device.pressBack()
    check(device.wait(Until.gone(listMarker), 3_000)) { "the app list did not close on Back" }
}

/** The search screen's only text input; its hint ("Search") is gone once a query is typed. */
private val searchField = By.clazz("android.widget.EditText").pkg(TARGET_PACKAGE)

/** Swipe up in the middle of Home, away from the right edge strip, the gesture that opens search. */
fun UiDevice.searchSwipeUp(steps: Int = 20) {
    val x = displayWidth / 2
    swipe(x, (displayHeight * 0.60f).toInt(), x, (displayHeight * 0.40f).toInt(), steps)
}

/** Opens search with the swipe up on Home and fails loudly if the field did not appear. */
fun MacrobenchmarkScope.openSearch() {
    device.searchSwipeUp()
    check(device.wait(Until.hasObject(searchField), 3_000)) { "search did not open after the swipe up" }
}

/** The first Back may only dismiss the keyboard, so press it until the field is gone. */
fun MacrobenchmarkScope.closeSearch() {
    repeat(2) {
        device.pressBack()
        if (device.wait(Until.gone(searchField), 1_500)) return
    }
    error("search did not close on Back")
}
