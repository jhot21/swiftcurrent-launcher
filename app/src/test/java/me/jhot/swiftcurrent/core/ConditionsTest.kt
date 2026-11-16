package me.jhot.swiftcurrent.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionsTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun entry_without_conditions_field_decodes_with_empty_list() {
        val old = """{"id":"a","target":{"type":"me.jhot.swiftcurrent.core.Target.App","component":"pkg.a/.Main"}}"""
        val entry = json.decodeFromString(Entry.serializer(), old)
        assertEquals(emptyList<Condition>(), entry.conditions)
    }

    @Test
    fun conditions_round_trip() {
        val entry = Entry(
            "a", Target.App("pkg.a/.Main"),
            conditions = listOf(Condition.Bluetooth(null, "Any", true), Condition.MeldMode("church", false)),
        )
        val back = json.decodeFromString(Entry.serializer(), json.encodeToString(Entry.serializer(), entry))
        assertEquals(entry, back)
    }

    @Test
    fun old_launcher_data_without_conditions_loads() {
        val old = """{"version":1,"favorites":[{"id":"a","target":{"type":"me.jhot.swiftcurrent.core.Target.App","component":"pkg.a/.Main"},"customLabel":"Browser"}]}"""
        val data = json.decodeFromString(LauncherData.serializer(), old)
        assertEquals(1, data.version)
        assertTrue(data.favorites.single().conditions.isEmpty())
    }
}
