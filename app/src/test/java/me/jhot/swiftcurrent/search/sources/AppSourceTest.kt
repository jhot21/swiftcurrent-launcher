package me.jhot.swiftcurrent.search.sources

import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.search.IconRef
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val notes = AppInfo("com.example.notes/.Main", "Notes", "com.example.notes")
private val vault = AppInfo("com.example.vault/.Main", "Vault", "com.example.vault")

class AppSourceTest {
    @Test
    fun hidden_apps_are_found_and_labeled_Hidden() {
        val source = AppSource({ listOf(notes, vault) }, { setOf(vault.component) }, { LaunchOutcome.Launched }, EnglishSearchStrings)
        val results = runBlocking { source.search("vault") }
        val r = results.single()
        assertEquals("Vault", r.label)
        assertEquals("Hidden", r.badge)
        assertEquals("Apps", r.sourceName)
        assertEquals(Kind.App, r.kind)
        assertEquals(IconRef.Package("com.example.vault"), r.icon)
        assertNull(runBlocking { source.search("notes") }.single().badge)
    }

    @Test
    fun a_new_list_is_searched_by_its_own_names_not_the_last_ones() {
        var list = listOf(notes)
        val source = AppSource({ list }, { emptySet() }, { LaunchOutcome.Launched }, EnglishSearchStrings)
        assertEquals("Notes", runBlocking { source.search("notes") }.single().label)
        list = listOf(notes.copy(label = "Jottings"))
        assertEquals("Jottings", runBlocking { source.search("jot") }.single().label)
        // The system's name still finds it after the owner renamed it.
        assertEquals("Jottings", runBlocking { source.search("notes") }.single().label)
    }

    @Test
    fun archived_apps_are_found_and_labeled_Archived() {
        val gone = AppInfo("com.example.gone/.Main", "Gone", "com.example.gone", archived = true)
        val source = AppSource({ listOf(notes, gone) }, { emptySet() }, { LaunchOutcome.Launched }, EnglishSearchStrings)
        assertEquals("Archived", runBlocking { source.search("gone") }.single().badge)
        assertNull(runBlocking { source.search("notes") }.single().badge)
    }

    @Test
    fun a_hidden_archived_app_says_Hidden() {
        val gone = AppInfo("com.example.gone/.Main", "Gone", "com.example.gone", archived = true)
        val source = AppSource({ listOf(gone) }, { setOf(gone.component) }, { LaunchOutcome.Launched }, EnglishSearchStrings)
        assertEquals("Hidden", runBlocking { source.search("gone") }.single().badge)
    }

    @Test
    fun launching_an_app_runs_the_launcher_and_returns_its_outcome() {
        // Recording is search's job once the launch succeeded, so the source only launches.
        val log = mutableListOf<String>()
        val outcome = LaunchOutcome.Failed("boom")
        val source = AppSource({ listOf(notes) }, { emptySet() }, { c -> log += "launch:$c"; outcome }, EnglishSearchStrings)
        val r = runBlocking { source.search("notes") }.single()
        assertEquals(outcome, r.primary.perform())
        assertEquals(listOf("launch:${notes.component}"), log)
    }

    @Test
    fun resolve_returns_only_known_ids() {
        val source = AppSource({ listOf(notes, vault) }, { setOf(vault.component) }, { LaunchOutcome.Launched }, EnglishSearchStrings)
        val rows = runBlocking {
            source.resolve(setOf("app:${vault.component}", "app:com.example.uninstalled/.Main", "shortcut:user/u1", "app:${notes.component}"))
        }
        // An uninstalled app and another source's id are simply absent; the rows are what search builds.
        assertEquals(listOf("app:${vault.component}", "app:${notes.component}"), rows.map { it.id })
        assertEquals(listOf("Vault", "Notes"), rows.map { it.label })
        assertEquals(listOf("Hidden", null), rows.map { it.badge })
        assertEquals(Kind.App, rows.first().kind)
    }
}
