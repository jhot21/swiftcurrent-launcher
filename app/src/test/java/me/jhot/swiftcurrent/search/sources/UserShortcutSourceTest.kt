package me.jhot.swiftcurrent.search.sources

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.Ranking
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.ShortcutFilter
import me.jhot.swiftcurrent.shortcuts.ShortcutProvider
import me.jhot.swiftcurrent.shortcuts.WebLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric: a web link's address is read back out of its stored Intent.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UserShortcutSourceTest {
    private val bob = UserShortcut("u1", "pkg.alpha.maps", "Friend Bob", "intent:#Intent;end", byteArrayOf(1))
    private val carol = UserShortcut("u2", "pkg.alpha.maps", "Friend Carol", "intent:#Intent;end")
    private val ghost = UserShortcut("u3", "pkg.alpha.maps", "   ", "intent:#Intent;end")
    private var list = listOf(bob, carol, ghost)
    private val launched = mutableListOf<String>()
    private val labels = mapOf("pkg.alpha.maps" to "Alpha Maps")
    private fun source(filter: ShortcutFilter = ShortcutFilter.All) =
        UserShortcutSource({ list }, labels::get, { launched += it.id; LaunchOutcome.Launched }, filter)

    private fun find(query: String, filter: ShortcutFilter = ShortcutFilter.All) =
        Ranking.order(query, runBlocking { source(filter).search(query) })

    @Test
    fun a_user_made_shortcut_is_found_by_name_labeled_App_colon_name() {
        val r = find("bob")
        assertEquals(listOf("Alpha Maps: Friend Bob"), r.map { it.label })
        assertEquals(Kind.Shortcut, r.single().kind)
        assertEquals("Shortcuts", r.single().sourceName)
        // The app name reaches them too; the blank-labelled one is never listed.
        assertEquals(2, find("alpha").size)
        r.single().primary.perform()
        assertEquals(listOf("u1"), launched)
    }

    @Test
    fun a_package_with_no_resolvable_app_label_is_skipped_not_shown_by_package_name() {
        list = listOf(UserShortcut("u9", "pkg.unknown", "Orphan", "intent:#Intent;end"))
        assertTrue(find("orphan").isEmpty())
    }

    @Test
    fun a_disabled_key_is_left_out() {
        val only = find("friend", ShortcutFilter { it != "user/u1" })
        assertEquals(listOf("Alpha Maps: Friend Carol"), only.map { it.label })
    }

    @Test
    fun the_shared_filter_also_hides_system_shortcuts() {
        val provider = object : ShortcutProvider {
            override fun access() = ShortcutAccess.Granted
            override fun entries() = listOf(
                ShortcutEntry("net.t/a", "Tasker", "net.t", "a", "Bedtime"),
                ShortcutEntry("net.t/b", "Tasker", "net.t", "b", "Bedroom"),
            )
            override fun launch(key: String) = LaunchOutcome.Launched
            override fun unpin(key: String) = LaunchOutcome.Launched
            override val changes = emptyFlow<Unit>()
        }
        val found = runBlocking { ShortcutSource(provider, filter = ShortcutFilter { it != "net.t/b" }).search("bed") }
        assertEquals(listOf("Tasker: Bedtime"), found.map { it.label })
        // Left out, the filter changes nothing for existing callers.
        assertEquals(2, runBlocking { ShortcutSource(provider).search("bed") }.size)
    }

    @Test
    fun resolve_returns_only_known_ids() {
        list = listOf(bob, carol, ghost, UserShortcut("u9", "pkg.unknown", "Orphan", "intent:#Intent;end"))
        val rows = runBlocking {
            source(ShortcutFilter { it != "user/u2" }).resolve(
                setOf("shortcut:user/u1", "shortcut:user/u2", "shortcut:user/u3", "shortcut:user/u9", "shortcut:user/gone", "app:pkg.alpha.maps/.Main"),
            )
        }
        // Deleted, switched off, blank, app-less and foreign ids are absent.
        assertEquals(listOf("shortcut:user/u1"), rows.map { it.id })
        assertEquals("Alpha Maps: Friend Bob", rows.single().label)
        rows.single().primary.perform()
        assertEquals(listOf("u1"), launched)
    }

    // The app label can be a binder call; it must not be asked again on every keystroke.
    @Test
    fun app_labels_are_resolved_once_until_the_list_or_the_names_change() {
        var asked = 0
        var version = Any()
        val source = UserShortcutSource({ list }, { asked++; labels[it] }, { LaunchOutcome.Launched }, labelsVersion = { version })
        runBlocking {
            source.search("f"); source.search("fr"); source.search("fri")
            val perShortcut = asked
            assertTrue("asked for each shortcut once", perShortcut in 1..list.size)
            source.search("frie")
            assertEquals("a later keystroke asks nothing", perShortcut, asked)

            version = Any()
            source.search("friend")
            assertEquals("new app names are read again", perShortcut * 2, asked)

            list = listOf(bob)
            source.search("friend")
            assertTrue("a new list is read again", asked > perShortcut * 2)
        }
    }

    @Test
    fun a_row_is_kept_between_keystrokes_with_its_folded_label() {
        val source = UserShortcutSource({ list }, labels::get, { LaunchOutcome.Launched })
        val first = runBlocking { source.search("bob") }.single()
        val second = runBlocking { source.search("bo") }.single { it.id == first.id }
        assertTrue("same row, folded once", first === second)
    }

    private val browser = "org.example.browser"
    private fun webLink(id: String, name: String, address: String, pkg: String = browser) =
        UserShortcut(id, pkg, name, WebLinks.intentUri(WebLinks.parse(address)!!, pkg))

    private val code = webLink("w1", "Code", "https://github.com/jhot21")
    private val geo = UserShortcut("g1", browser, "Friend Bob", "intent:geo:1,2#Intent;package=$browser;end")
    private val damaged = UserShortcut("d1", browser, "Broken", "intent:https://example.com#Intent;end")

    @Test
    fun a_web_link_is_found_by_its_address_as_well_as_its_name() {
        val labels = mapOf(browser to "Example Browser", "pkg.alpha.maps" to "Alpha Maps")
        listOf("gith", "github.com", "jhot21", "code").forEach { q ->
            list = listOf(code, geo, damaged)
            val found = Ranking.order(q, runBlocking { UserShortcutSource({ list }, labels::get, { LaunchOutcome.Launched }).search(q) })
            assertEquals("query '$q'", listOf("Example Browser: Code"), found.map { it.label })
        }
    }

    @Test
    fun the_address_is_carried_as_the_alias_and_other_shortcuts_have_none() {
        val labels = mapOf(browser to "Example Browser")
        val src = UserShortcutSource({ listOf(code, geo) }, labels::get, { LaunchOutcome.Launched })
        assertEquals("github.com/jhot21", Ranking.order("gith", runBlocking { src.search("gith") }).single().alias)
        assertNull(Ranking.order("bob", runBlocking { src.search("bob") }).single().alias)
    }

    @Test
    fun a_damaged_web_link_has_no_alias_and_is_still_found_by_name() {
        val labels = mapOf(browser to "Example Browser")
        val src = UserShortcutSource({ listOf(damaged) }, labels::get, { LaunchOutcome.Launched })
        val found = Ranking.order("broken", runBlocking { src.search("broken") })
        assertEquals(listOf("Example Browser: Broken"), found.map { it.label })
        assertNull(found.single().alias)
    }

    @Test
    fun an_empty_query_finds_nothing() {
        assertTrue(runBlocking { source().search("") }.isEmpty())
    }
}
