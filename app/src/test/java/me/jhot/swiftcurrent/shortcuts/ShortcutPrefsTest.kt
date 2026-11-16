package me.jhot.swiftcurrent.shortcuts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPrefsTest {
    private val store = MemoryLauncherStore()
    private val keys = mapOf("pkg.a" to listOf("pkg.a/one", "pkg.a/two", "user/u1"), "pkg.b" to listOf("pkg.b/x"))
    private val prefs = ShortcutPrefs(
        disabled = { store.data.settings.disabledShortcuts },
        edit = { f -> store.update { it.copy(settings = f(it.settings)) } },
        keysOf = { keys[it].orEmpty() },
    )

    @Test
    fun everything_is_enabled_until_switched_off_and_the_disabled_keys_are_persisted() = runBlocking {
        assertTrue(prefs.enabled("pkg.a/one"))
        prefs.setEnabled("pkg.a/one", false)
        assertFalse(prefs.enabled("pkg.a/one"))
        assertTrue(prefs.enabled("pkg.a/two"))
        assertEquals(setOf("pkg.a/one"), store.data.settings.disabledShortcuts)
        prefs.setEnabled("pkg.a/one", true)
        assertEquals(emptySet<String>(), store.data.settings.disabledShortcuts)
    }

    @Test
    fun toggle_all_switches_every_key_of_one_app_only() = runBlocking {
        prefs.setEnabled("pkg.b/x", false)
        prefs.setAll("pkg.a", false)
        assertEquals(setOf("pkg.a/one", "pkg.a/two", "user/u1", "pkg.b/x"), store.data.settings.disabledShortcuts)
        prefs.setAll("pkg.a", true)
        assertEquals(setOf("pkg.b/x"), store.data.settings.disabledShortcuts)
    }
}
