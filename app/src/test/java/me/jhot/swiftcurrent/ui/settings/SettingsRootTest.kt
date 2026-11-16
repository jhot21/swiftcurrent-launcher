package me.jhot.swiftcurrent.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.jhot.swiftcurrent.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SettingsRootTest {
    @get:Rule
    val compose = createComposeRule()

    private fun res(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun root_lists_exactly_home_apps_search_gestures_advanced() {
        val nav = SettingsNav()
        compose.setContent { SettingsRoot(nav) }
        val titles = listOf(R.string.settings_home, R.string.settings_apps, R.string.settings_search, R.string.settings_gestures, R.string.settings_advanced)
        titles.forEach { compose.onNodeWithText(res(it)).assertIsDisplayed() }
        // Order: each topic sits below the one before it.
        val tops = titles.map { compose.onNodeWithText(res(it)).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        // Nothing else is a row: every other clickable row would show up here.
        val clickable = compose.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes()
        assertEquals(5, clickable.size)
        compose.onNodeWithText(res(R.string.settings_search)).performClick()
        assertEquals(SettingsRoute.Search, nav.current.value)
    }

    @Test
    fun no_productivity_page_exists() {
        compose.setContent { SettingsRoot(SettingsNav()) }
        listOf("Productivity", "Themes", "Pro", "Community", "Feedback").forEach {
            compose.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
        assertEquals(
            setOf("Root", "Home", "Apps", "Search", "Gestures", "Advanced", "Calendars", "Spacing", "HiddenApps", "PopupFolders", "Shortcuts", "HiddenContactMethods", "SearchCalendars"),
            SettingsRoute.entries.map { it.name }.toSet(),
        )
    }
}
