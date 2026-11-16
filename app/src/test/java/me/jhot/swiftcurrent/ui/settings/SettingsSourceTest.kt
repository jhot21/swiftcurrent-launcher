package me.jhot.swiftcurrent.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// A source scan, so a new page cannot quietly hand-roll its own layout.
class SettingsSourceTest {
    private val dir = File("src/main/java/me/jhot/swiftcurrent/ui/settings")

    // Files that are a settings page; each must be built on SettingsPage.
    private val pages = setOf(
        "SettingsRoot.kt", "HomeSettings.kt", "AppsSettings.kt", "SearchSettings.kt", "GesturesSettings.kt",
        "AdvancedSettings.kt", "AppShortcutsPage.kt", "CalendarFilterList.kt", "SetupPage.kt",
    )

    // Logic and helpers with no layout of their own, and the one page that is deliberately not a list:
    // HomeSpacingPage is the real Home screen with controls floating over it, so the preview cannot drift.
    private val notPages = setOf(
        "SettingsNav.kt", "SettingsEdits.kt", "BackupController.kt", "AppShortcutGroups.kt",
        "SetupChecklist.kt", "AndroidSetupEnv.kt", "HomeSpacingPage.kt", "SearchHistoryExport.kt",
    )

    @Test
    fun every_settings_page_uses_SettingsPage() {
        val offenders = pages.filter { name ->
            val text = File(dir, name).also { assertTrue("missing $name", it.exists()) }.readText()
            !text.contains("SettingsPage(") || text.contains("LazyColumn(")
        }
        assertTrue("not built on SettingsPage: $offenders", offenders.isEmpty())
    }

    @Test
    fun no_unlisted_file_in_the_settings_package() {
        val unknown = dir.listFiles { f -> f.extension == "kt" }!!.map { it.name }.toSet() - pages - notPages
        assertTrue("classify these as a page or a helper in SettingsSourceTest: $unknown", unknown.isEmpty())
    }
}
