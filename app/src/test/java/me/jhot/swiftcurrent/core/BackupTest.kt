package me.jhot.swiftcurrent.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class CountingStore : LauncherStore {
    var saves = 0
    override suspend fun load() = LauncherData()
    override suspend fun update(transform: (LauncherData) -> LauncherData): LauncherData {
        saves++
        return LauncherData()
    }
}

class BackupTest {
    private fun sample(): LauncherData {
        val inner = Entry("i", Target.Shortcut("sc1"), customLabel = "Inner")
        val folder = Entry(
            "f", Target.Folder,
            popup = Popup(listOf(PopupRow.OwnShortcuts("pkg.a"), PopupRow.Item(inner))),
        )
        return LauncherData(
            favorites = listOf(Entry("a", Target.App("pkg.a/.Main"), "Browser"), folder),
            folders = listOf(folder, Entry("f2", Target.Folder, customLabel = "Spare", popup = Popup(emptyList()))),
            hiddenApps = setOf("pkg.hidden"),
            userShortcuts = listOf(
                UserShortcut("u1", "pkg.a", "Open thing", "intent:#Intent;end", byteArrayOf(1, 2, 3)),
            ),
            settings = Settings(sortByUsage = false, enabledCalendars = setOf(1L, 2L), disabledShortcuts = setOf("x")),
            launchCounts = mapOf("pkg.a" to 4),
        )
    }

    @Test
    fun backup_roundtrip_is_lossless() {
        val d = sample()
        val back = (Backup.import(Backup.export(d)) as ImportResult.Ok).data
        assertEquals(d, back)
    }

    @Test
    fun folders_survive_the_roundtrip_and_old_json_without_them_still_decodes() {
        val d = sample()
        assertEquals(2, (Backup.import(Backup.export(d)) as ImportResult.Ok).data.folders.size)
        val old = """{"version":1,"favorites":[{"id":"a","target":{"type":"me.jhot.swiftcurrent.core.Target.Folder"}}]}"""
        val decoded = Backup.import(old)
        assertTrue(decoded.toString(), decoded is ImportResult.Ok)
        assertEquals(emptyList<Entry>(), (decoded as ImportResult.Ok).data.folders)
        assertEquals(1, decoded.data.favorites.size)
    }

    @Test
    fun renamed_apps_and_folders_survive_export_and_import() {
        val app = Entry("a", Target.App("pkg.a/.Main"))
        val folder = Entry("f", Target.Folder, popup = Popup(listOf(PopupRow.Item(app))))
        val renamed = Renames.apply(
            Renames.apply(LauncherData(favorites = listOf(app, folder), folders = listOf(folder)), "a", "Browser"),
            "f", "Work \u2603",
        )
        val back = (Backup.import(Backup.export(renamed)) as ImportResult.Ok).data
        assertEquals(renamed, back)
        // An app's name is stored once, by app, and survives the round trip with the rest.
        assertEquals(mapOf("pkg.a/.Main" to "Browser"), back.appNames)
        assertEquals("Work \u2603", back.favorites[1].customLabel)
        assertEquals("Work \u2603", back.folders.single().customLabel)
    }

    @Test
    fun a_backup_made_before_apps_could_be_named_restores_with_no_names() {
        val old = """{"version":1,"favorites":[{"id":"a","target":{"type":"me.jhot.swiftcurrent.core.Target.App","component":"pkg.a/.Main"},"customLabel":"Browser"}]}"""
        val decoded = Backup.import(old) as ImportResult.Ok
        assertEquals(emptyMap<String, String>(), decoded.data.appNames)
        // The name an older version kept on the favorite is still the app's name.
        assertEquals("Browser", Renames.appNameOf(decoded.data, "pkg.a/.Main"))
    }

    @Test
    fun conditions_survive_export_and_import() {
        val conditions = listOf(
            Condition.Bluetooth("AA:BB:CC:DD:EE:01", "Car", true),
            Condition.Bluetooth(null, "Any", false),
            Condition.MeldMode("church", false),
        )
        val d = LauncherData(favorites = listOf(Entry("a", Target.App("pkg.a/.Main"), conditions = conditions)))
        val back = (Backup.import(Backup.export(d)) as ImportResult.Ok).data
        assertEquals(d, back)
        assertEquals(conditions, back.favorites.single().conditions)
    }

    @Test
    fun a_backup_without_conditions_still_imports_as_version_1() {
        val old = """{"version":1,"favorites":[{"id":"a","target":{"type":"me.jhot.swiftcurrent.core.Target.App","component":"pkg.a/.Main"}}]}"""
        val decoded = Backup.import(old) as ImportResult.Ok
        assertEquals(1, decoded.data.version)
        assertEquals(emptyList<Condition>(), decoded.data.favorites.single().conditions)
    }

    @Test
    fun old_stored_data_without_setupSeen_decodes_with_the_default() {
        val old = """{"version":1,"settings":{"sortByUsage":false}}"""
        val decoded = Backup.import(old) as ImportResult.Ok
        assertEquals(false, decoded.data.settings.setupSeen)
        assertEquals(false, decoded.data.settings.sortByUsage)
        val seen = LauncherData(settings = Settings(setupSeen = true))
        assertEquals(true, (Backup.import(Backup.export(seen)) as ImportResult.Ok).data.settings.setupSeen)
    }

    @Test
    fun hidden_contact_methods_roundtrip_and_old_data_has_none() {
        val h = HiddenContactAction("contact:1", "shortcut:k", "Bonne Hotmann", "Messages")
        val d = LauncherData(settings = Settings(hiddenContactActions = listOf(h)))
        assertEquals(listOf(h), (Backup.import(Backup.export(d)) as ImportResult.Ok).data.settings.hiddenContactActions)
        val old = """{"version":1,"settings":{"sortByUsage":false}}"""
        assertEquals(emptyList<HiddenContactAction>(), (Backup.import(old) as ImportResult.Ok).data.settings.hiddenContactActions)
    }

    @Test
    fun old_backup_without_new_settings_gets_defaults() {
        val old = """{"version":1,"settings":{"sortByUsage":false}}"""
        val s = (Backup.import(old) as ImportResult.Ok).data.settings
        assertEquals(true, s.appsFirst)
        assertEquals(true, s.contactsFirst)
        assertEquals(true, s.learnFromPicks)
    }

    @Test
    fun icon_bytes_are_stored_as_base64_text_not_a_number_array() {
        val json = Backup.export(sample())
        assertTrue(json, json.contains("\"iconBytes\":\"AQID\""))
    }

    @Test
    fun icon_bytes_decode_from_both_the_base64_and_the_old_number_array_shape() {
        fun decode(icon: String): UserShortcut {
            val text = """{"version":1,"userShortcuts":[{"id":"u","appPackage":"p","label":"L","intentUri":"i","iconBytes":$icon}]}"""
            return (Backup.import(text) as ImportResult.Ok).data.userShortcuts.single()
        }
        assertEquals(byteArrayOf(1, 2, 3).toList(), decode("\"AQID\"").iconBytes!!.toList())
        assertEquals(byteArrayOf(1, 2, -3).toList(), decode("[1,2,-3]").iconBytes!!.toList())
        assertEquals(null, decode("null").iconBytes)
        // A malformed icon costs only the icon: the shortcut and everything else still load.
        for (bad in listOf("\"!!not base64!!\"", "[1,\"x\",3]", "[1,2.5]", "{\"a\":1}", "true", "7")) {
            assertEquals(bad, null, decode(bad).iconBytes)
            assertEquals(bad, "L", decode(bad).label)
        }
    }

    @Test
    fun import_corrupt_json_is_rejected() {
        assertTrue(Backup.import("{not json") is ImportResult.Rejected)
        assertTrue(Backup.import("[1,2]") is ImportResult.Rejected)
        assertTrue(Backup.import("{\"version\":1,\"favorites\":5}") is ImportResult.Rejected)
    }

    @Test
    fun import_newer_version_is_rejected() {
        val newer = Backup.export(sample()).replace("\"version\":1", "\"version\":2")
        assertTrue(Backup.import(newer) is ImportResult.Rejected)
    }

    @Test
    fun rejected_import_never_writes() = runBlocking {
        val store = CountingStore()
        Backup.restore("{not json", store)
        Backup.restore(Backup.export(sample()).replace("\"version\":1", "\"version\":9"), store)
        assertEquals(0, store.saves)
        Backup.restore(Backup.export(sample()), store)
        assertEquals(1, store.saves)
    }
}
