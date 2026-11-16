package me.jhot.swiftcurrent.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Baselines in src/test/snapshots are the oracle for "every screen looks the same":
// re-record them only when the design is meant to change.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class DesignScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")

    @Composable
    private fun IconStub() {
        Box(Modifier.size(24.dp).border(2.dp, Tokens.text, CircleShape))
    }

    @Composable
    private fun SampleSettings(state: LazyListState) {
        SettingsPage(title = "Sample settings", state = state) {
            section("Basics")
            row(icon = { IconStub() }, title = "Home", description = "Clock, agenda and favorites")
            row(icon = { IconStub() }, title = "Apps", description = "List order and hidden apps")
            row(icon = { IconStub() }, title = "Search", description = "Sources and shortcuts")
            row(icon = { IconStub() }, title = "Gestures", description = "Scrubber, swipes and haptics")
            section("More")
            row(icon = { IconStub() }, title = "Advanced")
            row(icon = { IconStub() }, title = "Backup", description = "Save and restore your setup")
            row(icon = { IconStub() }, title = "About")
            row(icon = { IconStub() }, title = "Help")
            row(icon = { IconStub() }, title = "Licenses")
            // Enough rows to overflow the screen, otherwise the list cannot scroll and never collapses.
            (1..8).forEach { row(icon = { IconStub() }, title = "Extra $it", description = "Filler row") }
        }
    }

    @Test
    fun settingsPage_expanded() {
        compose.setContent { SampleSettings(rememberLazyListState()) }
        shot("settingsPage_expanded")
    }

    @Test
    fun settingsPage_collapsed() {
        val state = LazyListState()
        compose.setContent { SampleSettings(state) }
        compose.runOnIdle { runBlocking { state.scrollToItem(2) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        shot("settingsPage_collapsed")
    }

    @Test
    fun editorPage_withCounter() {
        compose.setContent {
            EditorPage(title = "Your favorites", counter = "3 selected · 8 recommended", onDone = {}) {
                item { AppText("Selected", AppType.label) }
                listOf("Alpha", "Bravo", "Charlie").forEach { name ->
                    item {
                        AppText(name, AppType.body)
                    }
                }
            }
        }
        shot("editorPage_withCounter")
    }

    @Test
    fun sheetScaffold_agendaStyle() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                SheetScaffold(title = "Your agenda", onDismiss = {}) {
                    SheetCard(icon = { IconStub() }, title = "Sample event", description = "9:00 AM to 9:30 AM")
                    SheetCard(icon = { IconStub() }, title = "Another event", description = "1:00 PM to 2:00 PM")
                    SheetCard(icon = { IconStub() }, title = "Third event")
                }
            }
        }
        shot("sheetScaffold_agendaStyle")
    }
}
