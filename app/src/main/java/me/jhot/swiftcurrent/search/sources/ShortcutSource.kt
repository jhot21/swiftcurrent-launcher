package me.jhot.swiftcurrent.search.sources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.search.Action
import me.jhot.swiftcurrent.search.IconRef
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.QueryWords
import me.jhot.swiftcurrent.search.SearchResult
import me.jhot.swiftcurrent.search.SearchSource
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.ShortcutFilter
import me.jhot.swiftcurrent.shortcuts.ShortcutProvider

/**
 * Searches a cached index of the system's shortcuts. Reading them is binder IPC plus a package-label
 * lookup per app, so it happens when search opens ([refresh]) and when the provider reports a change
 * ([followChanges]), never per keystroke. The first query reads it if nothing has yet. [filter]
 * drops shortcuts the owner switched off.
 */
class ShortcutSource(
    private val provider: ShortcutProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val filter: ShortcutFilter = ShortcutFilter.All,
) : SearchSource {
    override val name = "Shortcuts"

    // The index as result rows, built once per read: the label, its folded form and the launch action are
    // not rebuilt per keystroke. The owner's switches are applied per query, since they change without a read.
    @Volatile
    private var cache: List<SearchResult>? = null
    private val firstLoad = Mutex()

    // Both reads are binder calls, so they stay off the collector's (often main) thread.
    private suspend fun read(): List<SearchResult> = withContext(dispatcher) {
        if (provider.access() == ShortcutAccess.Granted) provider.entries().mapNotNull(::row) else emptyList()
    }

    /** Reads the index again. A failure leaves the previous index in place and is thrown to the caller. */
    suspend fun refresh() {
        // Under the first-load lock so a query that arrives mid-refresh waits for it instead of reading twice.
        firstLoad.withLock { cache = read() }
    }

    /** Keeps the index current for as long as the caller stays in it; a failed refresh keeps the old index. */
    suspend fun followChanges() {
        provider.changes.collect {
            try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The old index stays; the next change or the next open reads it again.
            }
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        val words = QueryWords.of(query)
        if (words.isEmpty) return emptyList()
        return rows().filter { filter.enabled(keyOf(it)) && words.matches(it.foldedLabel) }
    }

    // Awaits a cold index like search does: refresh() runs on every open, and recents must not miss it.
    override suspend fun resolve(ids: Set<String>): List<SearchResult> {
        val byId = rows().associateBy { it.id }
        return ids.mapNotNull { id -> byId[id]?.takeIf { filter.enabled(keyOf(it)) } }
    }

    private suspend fun rows() = cache ?: firstLoad.withLock { cache ?: read().also { cache = it } }

    private fun keyOf(row: SearchResult) = row.id.removePrefix("shortcut:")

    private fun row(e: ShortcutEntry): SearchResult? {
        if (e.label.isBlank()) return null
        val label = "${e.appLabel}: ${e.label}"
        return SearchResult(
            id = "shortcut:${e.key}",
            label = label,
            sourceName = name,
            kind = Kind.Shortcut,
            icon = IconRef.ShortcutId(e.packageName, e.id),
            primary = Action(label, IconRef.ShortcutId(e.packageName, e.id)) { provider.launch(e.key) },
            appLabel = e.appLabel,
            entryLabel = e.label,
            packageName = e.packageName,
        )
    }
}
