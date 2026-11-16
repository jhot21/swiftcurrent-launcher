package me.jhot.swiftcurrent.ui.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.sources.ShortcutSource
import me.jhot.swiftcurrent.search.sources.UserShortcutSource
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.shortcuts.MemoryLauncherStore
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.ShortcutPrefs
import me.jhot.swiftcurrent.shortcuts.ShortcutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShortcutGroupsTest {
    private val alpha = "pkg.alpha.maps"
    private val system = listOf(
        ShortcutEntry("$alpha/home", "Alpha Maps", alpha, "home", "Go home"),
        ShortcutEntry("$alpha/work", "Alpha Maps", alpha, "work", "Go to work"),
        ShortcutEntry("pkg.bravo.mail/new", "Bravo Mail", "pkg.bravo.mail", "new", "Compose"),
    )
    private val bob = UserShortcut("u1", alpha, "Friend Bob", "intent:#Intent;end")
    private val creators = listOf(
        Creator("$alpha/.Friend", "Friend's location", "Alpha Maps"),
        Creator("pkg.charlie.notes/.Make", "New note", "Charlie Notes"),
    )
    private val labels = mapOf(alpha to "Alpha Maps", "pkg.bravo.mail" to "Bravo Mail", "pkg.charlie.notes" to "Charlie Notes")

    private fun build(disabled: Set<String> = emptySet(), user: List<UserShortcut> = listOf(bob)) =
        AppShortcutGroups.build(system, user, creators, disabled) { labels[it] ?: it }

    @Test
    fun user_made_group_is_separate_from_app_group() {
        val g = build().single { it.packageName == alpha }
        assertEquals(listOf("Go home", "Go to work"), g.fromApp.map { it.label })
        assertEquals(listOf("Friend Bob"), g.userMade.map { it.label })
        assertEquals(listOf("user/u1"), g.userMade.map { it.key })
        assertEquals(3, g.count)
        assertEquals(listOf("Friend's location"), g.creators.map { it.label })
    }

    @Test
    fun apps_are_sorted_and_include_apps_that_only_offer_creators() {
        assertEquals(listOf("Alpha Maps", "Bravo Mail", "Charlie Notes"), build().map { it.appLabel })
        val notes = build().last()
        assertEquals(0, notes.count)
        assertTrue(notes.fromApp.isEmpty() && notes.userMade.isEmpty())
    }

    @Test
    fun a_user_shortcut_of_an_app_with_no_other_entry_still_gets_a_group() {
        val orphan = UserShortcut("u2", "pkg.delta.tools", "Thing", "intent:#Intent;end")
        assertTrue(build(user = listOf(orphan)).any { it.packageName == "pkg.delta.tools" && it.count == 1 })
    }

    @Test
    fun the_disabled_keys_decide_each_rows_toggle() {
        val g = build(disabled = setOf("$alpha/work", "user/u1")).single { it.packageName == alpha }
        assertEquals(listOf(true, false), g.fromApp.map { it.enabled })
        assertEquals(listOf(false), g.userMade.map { it.enabled })
        assertFalse(g.allOn)
        assertTrue(build().single { it.packageName == alpha }.allOn)
    }

    @Test
    fun permission_badge_flag_set_for_sources_that_need_a_grant() {
        val denied = AppShortcutGroups.sources(contactsGranted = false)
        assertEquals(listOf(SourceId.Apps, SourceId.Shortcuts, SourceId.Contacts), denied.map { it.id })
        assertEquals(listOf(SourceId.Contacts), denied.filter { it.missingPermission }.map { it.id })
        assertTrue(AppShortcutGroups.sources(contactsGranted = true).none { it.missingPermission })
    }

    @Test
    fun permission_badge_flag_set_for_the_shortcuts_source_when_not_default_home() {
        val notHome = AppShortcutGroups.sources(contactsGranted = true, shortcutsGranted = false)
        assertEquals(listOf(SourceId.Shortcuts), notHome.filter { it.missingPermission }.map { it.id })
        assertTrue(AppShortcutGroups.sources(contactsGranted = true, shortcutsGranted = true).none { it.missingPermission })
        val both = AppShortcutGroups.sources(contactsGranted = false, shortcutsGranted = false)
        assertEquals(setOf(SourceId.Shortcuts, SourceId.Contacts), both.filter { it.missingPermission }.map { it.id }.toSet())
    }

    private val provider = object : ShortcutProvider {
        override fun access() = ShortcutAccess.Granted
        override fun entries() = system
        override fun launch(key: String) = LaunchOutcome.Launched
        override fun unpin(key: String) = LaunchOutcome.Launched
        override val changes = emptyFlow<Unit>()
    }

    private fun searchAll(store: MemoryLauncherStore, query: String): List<String> {
        val prefs = prefsFor(store)
        val user = UserShortcutSource({ store.data.userShortcuts }, labels::get, { LaunchOutcome.Launched }, prefs)
        return runBlocking { ShortcutSource(provider, filter = prefs).search(query) + user.search(query) }.map { it.label }
    }

    private fun prefsFor(store: MemoryLauncherStore) = ShortcutPrefs(
        disabled = { store.data.settings.disabledShortcuts },
        edit = { f -> store.update { it.copy(settings = f(it.settings)) } },
        keysOf = { app -> build(store.data.settings.disabledShortcuts, store.data.userShortcuts).first { it.packageName == app }.keys },
    )

    @Test
    fun toggle_all_off_hides_every_shortcut_of_that_app_from_search() = runBlocking {
        val store = MemoryLauncherStore(me.jhot.swiftcurrent.core.LauncherData(userShortcuts = listOf(bob)))
        assertEquals(setOf("Alpha Maps: Go home", "Alpha Maps: Friend Bob", "Alpha Maps: Go to work"), searchAll(store, "alpha").toSet())
        prefsFor(store).setAll(alpha, false)
        assertEquals(emptyList<String>(), searchAll(store, "alpha"))
        // Another app's shortcuts are untouched, and switching back on restores them.
        assertEquals(listOf("Bravo Mail: Compose"), searchAll(store, "compose"))
        prefsFor(store).setAll(alpha, true)
        assertEquals(3, searchAll(store, "alpha").size)
    }

    @Test
    fun deleted_shortcut_is_removed_from_store_and_search() = runBlocking {
        val store = MemoryLauncherStore(me.jhot.swiftcurrent.core.LauncherData(userShortcuts = listOf(bob)))
        assertEquals(listOf("Alpha Maps: Friend Bob"), searchAll(store, "bob"))
        me.jhot.swiftcurrent.shortcuts.UserShortcuts(
            { }, me.jhot.swiftcurrent.shortcuts.LaunchMessages("a", "b", "c"),
        ).delete("u1", store::update)
        assertEquals(emptyList<UserShortcut>(), store.data.userShortcuts)
        assertEquals(emptyList<String>(), searchAll(store, "bob"))
    }
}
