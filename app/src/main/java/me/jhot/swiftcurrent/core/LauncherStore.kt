package me.jhot.swiftcurrent.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Applies one change and returns what is now stored. The store and the shell's live data both
 * implement it, so a caller (a shortcut being added, say) can be written once against either
 * without ever reading the whole blob back.
 */
typealias LauncherUpdate = suspend ((LauncherData) -> LauncherData) -> LauncherData

interface LauncherStore {
    suspend fun load(): LauncherData

    /**
     * Reads, transforms and writes as one step, so concurrent writers cannot lose each other's
     * edits. Returns what was written, so callers need not read the whole blob back.
     */
    suspend fun update(transform: (LauncherData) -> LauncherData): LauncherData
}

/**
 * Launch counts left in DataStore by versions that kept them there, read once so they can move to the
 * database. [read] is null when none are left.
 */
interface LegacyCounts {
    suspend fun read(): Map<String, Int>?

    suspend fun drop()
}

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore("launcher")
private val DATA_KEY = stringPreferencesKey("data")

private val UNREADABLE_KEY = stringPreferencesKey("unreadable")
private val COUNTS_KEY = stringPreferencesKey("launchCounts")
private val COUNTS_SERIALIZER = MapSerializer(String.serializer(), Int.serializer())
private val countsJson = Json
private const val EMPTY_BLOB_COUNTS = "\"launchCounts\":{}"

/**
 * Stores the same versioned JSON the backup uses, so one format covers disk and export. Launch counts live in the
 * database now: [load] and [update] always give empty counts, so a write can never put stale ones back. Counts an
 * older version left here (under their own key, or before that inside the blob) are only reachable as [LegacyCounts].
 */
class DataStoreLauncherStore(private val store: DataStore<Preferences>) : LauncherStore, LegacyCounts {
    constructor(context: Context) : this(context.applicationContext.launcherDataStore)

    override suspend fun load(): LauncherData {
        val prefs = store.data.first()
        val text = prefs[DATA_KEY] ?: return LauncherData()
        return when (val r = Backup.import(text)) {
            is ImportResult.Ok -> r.data.copy(launchCounts = emptyMap())
            is ImportResult.Rejected -> {
                // Copy before returning defaults: the next save overwrites the data key, and this
                // may be the only copy of the user's setup. A newer unreadable blob replaces an
                // older preserved one; only the latest is kept.
                store.edit { it[UNREADABLE_KEY] = text }
                LauncherData()
            }
        }
    }

    override suspend fun update(transform: (LauncherData) -> LauncherData): LauncherData {
        lateinit var written: LauncherData
        store.edit { prefs ->
            val text = prefs[DATA_KEY]
            var unreadable = false
            val current = when (val r = text?.let { Backup.import(it) }) {
                null -> LauncherData()
                is ImportResult.Ok -> r.data
                is ImportResult.Rejected -> {
                    // Same rule as load(): keep the raw text before the write replaces it.
                    prefs[UNREADABLE_KEY] = text
                    unreadable = true
                    LauncherData()
                }
            }
            written = transform(current.copy(launchCounts = emptyMap())).copy(launchCounts = emptyMap())
            // Compared with the blob as stored, counts included, so a blob that still carries counts is rewritten without them.
            if (unreadable || text == null || written != current) prefs[DATA_KEY] = Backup.export(written)
        }
        return written
    }

    override suspend fun read(): Map<String, Int>? {
        val prefs = store.data.first()
        readCounts(prefs[COUNTS_KEY])?.let { return it }
        return prefs[DATA_KEY]?.let { blobCounts(it) }
    }

    override suspend fun drop() {
        store.edit { prefs ->
            prefs.remove(COUNTS_KEY)
            val r = prefs[DATA_KEY]?.let { Backup.import(it) } as? ImportResult.Ok ?: return@edit
            if (r.data.launchCounts.isNotEmpty()) prefs[DATA_KEY] = Backup.export(r.data.copy(launchCounts = emptyMap()))
        }
    }

    /** Null when absent or damaged: counts only order the app list, so losing them must not fail the load. */
    private fun readCounts(text: String?): Map<String, Int>? =
        text?.let { runCatching { countsJson.decodeFromString(COUNTS_SERIALIZER, it) }.getOrNull() }

    // Only the one field is decoded: this runs on every start, and the rest of the blob (icons included) is not needed.
    // Every write leaves the counts empty in the encoder's own spelling, so after the first start the blob is not
    // parsed at all; a value inside a string has its quotes escaped and cannot match.
    private fun blobCounts(text: String): Map<String, Int>? = runCatching {
        if ("\"launchCounts\"" !in text || EMPTY_BLOB_COUNTS in text) return null
        val field = countsJson.parseToJsonElement(text).jsonObject["launchCounts"] ?: return null
        countsJson.decodeFromJsonElement(COUNTS_SERIALIZER, field)
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** The raw text of the last stored data that could not be read, for a recovery offer. */
    suspend fun unreadableBackup(): String? = store.data.first()[UNREADABLE_KEY]

    /** Drops the preserved text after the owner restored it, so the recovery offer goes away. */
    suspend fun clearUnreadableBackup() {
        store.edit { it.remove(UNREADABLE_KEY) }
    }
}
