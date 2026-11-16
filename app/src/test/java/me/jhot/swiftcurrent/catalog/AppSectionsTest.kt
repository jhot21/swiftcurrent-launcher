package me.jhot.swiftcurrent.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSectionsTest {
    private fun app(label: String) = AppInfo("com.x/$label", label, "com.x")

    private fun build(vararg labels: String, hidden: Set<String> = emptySet()) =
        AppSections.build(labels.map(::app), hidden, emptyMap(), sortByUsage = false)

    @Test
    fun digits_and_symbols_go_under_hash() {
        val s = build("1Password", "_tmp", "Alpha")
        assertEquals(listOf('#', 'A'), s.map { it.key })
        assertEquals(listOf("1Password", "_tmp"), s[0].apps.map { it.label })
    }

    @Test
    fun letters_follow_alphabet_and_empty_letters_are_omitted() {
        val s = build("zeta", "Bravo", "alpha", "Beta")
        assertEquals(listOf('A', 'B', 'Z'), s.map { it.key })
        assertEquals(listOf("Beta", "Bravo"), s[1].apps.map { it.label })
    }

    @Test
    fun diacritic_first_letter_files_under_base_letter() {
        val s = build("Émail", "Eagle", "Ångström")
        assertEquals(listOf('A', 'E'), s.map { it.key })
        assertEquals(listOf("Eagle", "Émail"), s[1].apps.map { it.label })
    }

    @Test
    fun hidden_apps_are_excluded() {
        val s = build("Alpha", "Apex", hidden = setOf("com.x/Apex"))
        assertEquals(listOf("Alpha"), s.single().apps.map { it.label })
    }
}
