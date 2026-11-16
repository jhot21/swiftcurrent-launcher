package me.jhot.swiftcurrent.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface ImportResult {
    data class Ok(val data: LauncherData) : ImportResult
    data class Rejected(val reason: String) : ImportResult
}

object Backup {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun export(d: LauncherData): String = json.encodeToString(LauncherData.serializer(), d)

    /** Never throws; the version is read before anything else is decoded. */
    fun import(json: String): ImportResult {
        val root = try {
            this.json.parseToJsonElement(json) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return ImportResult.Rejected("not a backup file")
        val version = try {
            root["version"]?.jsonPrimitive?.int
        } catch (_: Exception) {
            null
        } ?: return ImportResult.Rejected("missing version")
        if (version > LauncherData.CURRENT_VERSION) {
            return ImportResult.Rejected("made by a newer version (v$version)")
        }
        return try {
            ImportResult.Ok(this.json.decodeFromJsonElement(LauncherData.serializer(), root))
        } catch (_: Exception) {
            ImportResult.Rejected("backup is damaged")
        }
    }

    /** The caller pattern: data is replaced only when the import is accepted. */
    suspend fun restore(json: String, store: LauncherStore): ImportResult =
        import(json).also { result -> if (result is ImportResult.Ok) store.update { result.data } }
}
