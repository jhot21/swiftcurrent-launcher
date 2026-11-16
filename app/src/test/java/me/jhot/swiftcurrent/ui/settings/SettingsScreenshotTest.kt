package me.jhot.swiftcurrent.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Baselines for the settings front page and some topic pages. Re-record only when the design is meant to change.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun screenshots_match_for_root_and_one_subpage() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            if (page == 0) SettingsRoot(SettingsNav())
            else HomeSettings(calendarGranted = true, onFavorites = {}, onCalendars = {}, onGrantCalendar = {})
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/settings_root.png")
        page = 1
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/settings_home.png")
    }

    @Test
    fun search_and_advanced_pages() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            if (page == 0) {
                SearchSettings(
                    shortcutsWork = true, onShortcuts = {}, onOpenHomeSettings = {},
                    appsFirst = true, contactsFirst = false, learnFromPicks = true,
                    onAppsFirst = {}, onContactsFirst = {}, onLearnFromPicks = {}, onClearHistory = {},
                )
            } else {
                AdvancedSettings(hasUnreadable = false, onBackUp = {}, onRestore = {}, onRecover = {}, onSetup = {}, onExportSearchHistory = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/settings_search.png")
        page = 1
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/settings_advanced.png")
    }

    @Test
    fun setup_page() {
        compose.setContent {
            SetupPage(
                steps = listOf(
                    SetupStep(StepKind.DefaultLauncher, true),
                    SetupStep(StepKind.Contacts, false),
                    SetupStep(StepKind.Calendar, false),
                    SetupStep(StepKind.Accessibility, false),
                ),
                blocked = emptySet(),
                onAction = {},
                onFinish = {},
            )
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/setup_page.png")
    }
}
