package me.jhot.swiftcurrent.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreLauncherStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dataKey = stringPreferencesKey("data")

    private fun fresh() = PreferenceDataStoreFactory.create { tmp.root.resolve("t.preferences_pb") }

    private fun write(ds: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>, text: String) =
        runBlocking { ds.edit { it[dataKey] = text } }

    private val newer = Backup.export(LauncherData()).replace("\"version\":1", "\"version\":9")

    @Test
    fun a_corrupt_icon_does_not_make_the_store_unreadable() = runBlocking {
        val ds = fresh()
        write(
            ds,
            """{"version":1,"favorites":[{"id":"a","target":{"type":"me.jhot.swiftcurrent.core.Target.App","component":"p/.M"}}],""" +
                """"userShortcuts":[{"id":"u","appPackage":"p","label":"L","intentUri":"i","iconBytes":"!!bad!!"}],""" +
                """"settings":{"sortByUsage":false}}""",
        )
        val store = DataStoreLauncherStore(ds)
        val d = store.load()
        assertEquals(listOf("a"), d.favorites.map { it.id })
        assertEquals(false, d.settings.sortByUsage)
        assertNull(d.userShortcuts.single().iconBytes)
        assertEquals("L", d.userShortcuts.single().label)
        assertNull(store.unreadableBackup())
    }

    @Test
    fun load_of_corrupt_text_returns_defaults_and_keeps_the_raw_text() = runBlocking {
        val ds = fresh()
        write(ds, "{not json")
        val store = DataStoreLauncherStore(ds)
        assertEquals(LauncherData(), store.load())
        assertEquals("{not json", store.unreadableBackup())
    }

    @Test
    fun load_of_newer_version_returns_defaults_and_keeps_the_raw_text() = runBlocking {
        val ds = fresh()
        write(ds, newer)
        val store = DataStoreLauncherStore(ds)
        assertEquals(LauncherData(), store.load())
        assertEquals(newer, store.unreadableBackup())
    }

    @Test
    fun update_after_a_failed_load_does_not_erase_the_preserved_text() = runBlocking {
        val ds = fresh()
        write(ds, "{not json")
        val store = DataStoreLauncherStore(ds)
        store.update { it.copy(hiddenApps = setOf("a")) }
        assertEquals("{not json", store.unreadableBackup())
        assertEquals(setOf("a"), store.load().hiddenApps)
    }

    @Test
    fun load_of_valid_data_does_not_touch_the_unreadable_key() = runBlocking {
        val store = DataStoreLauncherStore(fresh())
        store.update { LauncherData(hiddenApps = setOf("p")) }
        assertEquals(setOf("p"), store.load().hiddenApps)
        assertNull(store.unreadableBackup())
    }

    @Test
    fun interleaved_updates_both_land() = runBlocking {
        val store = DataStoreLauncherStore(fresh())
        val a = async(Dispatchers.Default) {
            repeat(20) { n -> store.update { d -> d.copy(appNames = d.appNames + ("a$n" to "A")) } }
        }
        val b = async(Dispatchers.Default) {
            repeat(20) { n -> store.update { d -> d.copy(hiddenApps = d.hiddenApps + "p$n") } }
        }
        a.await(); b.await()
        val d = store.load()
        assertEquals(20, d.appNames.size)
        assertEquals(20, d.hiddenApps.size)
    }

    @Test
    fun update_returns_what_it_wrote() = runBlocking {
        val store = DataStoreLauncherStore(fresh())
        val written = store.update { it.copy(hiddenApps = setOf("p")) }
        assertEquals(setOf("p"), written.hiddenApps)
        assertEquals(setOf("p"), store.load().hiddenApps)
    }

    @Test
    fun update_of_unreadable_text_preserves_it_and_transforms_defaults() = runBlocking {
        val ds = fresh()
        write(ds, "{not json")
        val store = DataStoreLauncherStore(ds)
        store.update { it.copy(hiddenApps = setOf("p")) }
        assertEquals("{not json", store.unreadableBackup())
        assertEquals(setOf("p"), store.load().hiddenApps)
    }

    private val countsKey = stringPreferencesKey("launchCounts")

    private fun raw(ds: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>, key: androidx.datastore.preferences.core.Preferences.Key<String>) =
        runBlocking { ds.data.first()[key] }

    private val counted = Backup.export(LauncherData(hiddenApps = setOf("x"), launchCounts = mapOf("a" to 2)))

    @Test
    fun load_never_returns_counts() = runBlocking {
        val ds = fresh()
        write(ds, counted)
        runBlocking { ds.edit { it[countsKey] = """{"a":5}""" } }
        val d = DataStoreLauncherStore(ds).load()
        assertEquals(emptyMap<String, Int>(), d.launchCounts)
        assertEquals(setOf("x"), d.hiddenApps)
    }

    @Test
    fun update_strips_counts_from_the_blob() = runBlocking {
        val ds = fresh()
        write(ds, counted)
        val store = DataStoreLauncherStore(ds)

        val written = store.update { it.copy(hiddenApps = it.hiddenApps + "y", launchCounts = mapOf("b" to 1)) }

        assertEquals(emptyMap<String, Int>(), written.launchCounts)
        assertEquals(setOf("x", "y"), written.hiddenApps)
        val blob = (Backup.import(raw(ds, dataKey)!!) as ImportResult.Ok).data
        assertEquals(emptyMap<String, Int>(), blob.launchCounts)
        assertEquals(setOf("x", "y"), blob.hiddenApps)
        assertNull(raw(ds, countsKey))
    }

    @Test
    fun legacy_read_prefers_the_key_then_the_blob() = runBlocking {
        val ds = fresh()
        val store = DataStoreLauncherStore(ds)
        assertNull(store.read())

        write(ds, Backup.export(LauncherData()))
        assertNull(store.read())

        write(ds, counted)
        assertEquals(mapOf("a" to 2), store.read())

        runBlocking { ds.edit { it[countsKey] = """{"a":5}""" } }
        assertEquals(mapOf("a" to 5), store.read())

        // A damaged key loses only itself: the blob's counts are still found.
        runBlocking { ds.edit { it[countsKey] = "{oops" } }
        assertEquals(mapOf("a" to 2), store.read())
    }

    @Test
    fun legacy_read_of_a_blob_finds_counts_only_where_there_are_some() = runBlocking {
        val ds = fresh()
        val store = DataStoreLauncherStore(ds)
        // The store skips parsing a blob in this spelling, so the encoder must still write empty counts this way.
        val empty = Backup.export(LauncherData(hiddenApps = setOf("x")))
        assertEquals(true, empty.contains("\"launchCounts\":{}"))
        write(ds, empty)
        assertNull(store.read())

        write(ds, """{"version":1,"hiddenApps":["x"]}""")
        assertNull(store.read())

        write(ds, Backup.export(LauncherData(launchCounts = mapOf("a" to 2))))
        assertEquals(mapOf("a" to 2), store.read())

        // Not the encoder's spelling: still parsed, not mistaken for empty.
        write(ds, """{"version":1, "launchCounts" : {"a":3}}""")
        assertEquals(mapOf("a" to 3), store.read())
    }

    @Test
    fun legacy_drop_removes_key_and_blob_counts() = runBlocking {
        val ds = fresh()
        write(ds, counted)
        runBlocking { ds.edit { it[countsKey] = """{"a":5}""" } }
        val store = DataStoreLauncherStore(ds)

        store.drop()

        assertNull(raw(ds, countsKey))
        assertNull(store.read())
        val blob = (Backup.import(raw(ds, dataKey)!!) as ImportResult.Ok).data
        assertEquals(LauncherData(hiddenApps = setOf("x")), blob)
    }

    @Test
    fun legacy_drop_leaves_a_blob_without_counts_untouched() = runBlocking {
        val ds = fresh()
        // Not the encoder's own spelling, so a rewrite would show.
        val text = Backup.export(LauncherData(hiddenApps = setOf("x"))).replace("{", "{ ")
        write(ds, text)
        DataStoreLauncherStore(ds).drop()
        assertEquals(text, raw(ds, dataKey))
    }
}
