package me.jhot.swiftcurrent.search

import me.jhot.swiftcurrent.search.sources.ContactActionKey
import me.jhot.swiftcurrent.search.sources.ContactActionKey.Method

/** The phone's default messaging and dialer apps, whose shortcuts for a person are what Message and Call already do. */
data class DefaultApps(val sms: String? = null, val dialer: String? = null)

/**
 * An app's shortcut for a person ("Gmail: Alex Example") is another way to reach that contact. Only
 * the top result gets its ways of reaching it spelled out, so with several people matching a search
 * the list stays one line each and easy to scan. Two steps around ranking:
 *
 * - [mark] counts, for each contact, the shortcuts named after it, so it can rank above other shortcuts.
 * - [foldIntoTop], after ranking, moves those shortcuts onto the contact when it is the top result
 *   (named after the app) and drops them from the list. Contacts further down keep their shortcuts as
 *   ordinary rows, so nothing becomes unreachable.
 *
 * A shortcut of the default SMS app or dialer is dropped when the contact has its own Message or Call button, since
 * that button opens the same conversation or call; with no such button the shortcut stays, so no route is lost.
 *
 * Without a matching contact, or with contacts access off, shortcuts stay as they are.
 */
object ContactFolding {
    fun mark(results: List<SearchResult>): List<SearchResult> {
        val owner = ownerOf(results)
        if (owner.isEmpty()) return results
        val counts = owner.values.groupingBy { it }.eachCount()
        return results.map { r -> if (r.kind == Kind.Contact) r.copy(folded = counts[r.id] ?: 0) else r }
    }

    fun foldIntoTop(ranked: List<SearchResult>, defaults: DefaultApps = DefaultApps()): List<SearchResult> {
        val top = ranked.firstOrNull() ?: return ranked
        if (top.kind != Kind.Contact) return ranked
        val owner = ownerOf(ranked)
        val mine = ranked.filter { owner[it.id] == top.id }
        if (mine.isEmpty()) return ranked
        val own = top.secondary.mapNotNull { it.recentId?.let(ContactActionKey::parse)?.method }.toSet()
        fun redundant(r: SearchResult) = r.packageName != null &&
            ((r.packageName == defaults.sms && Method.Message in own) || (r.packageName == defaults.dialer && Method.Call in own))
        // Each keeps its shortcut's id, so a pick of it can be offered again as that shortcut.
        val moved = mine.filterNot(::redundant).map { Action(it.appLabel!!, it.primary.icon, recentId = it.id, perform = it.primary.perform) }
        val drop = mine.map { it.id }.toSet()
        return ranked.filter { it.id !in drop }.map { r ->
            if (r.id == top.id) r.copy(secondary = r.secondary + moved) else r
        }
    }

    /** Shortcut id to the id of the contact it is named after. */
    private fun ownerOf(results: List<SearchResult>): Map<String, String> {
        val contacts = results.filter { it.kind == Kind.Contact }
        if (contacts.isEmpty()) return emptyMap()
        // Two contacts with the same name are indistinguishable by a shortcut's label, so neither owns it.
        val byName = HashMap<String, SearchResult>()
        for ((name, same) in contacts.groupBy { it.foldedLabel.trim() }) {
            if (same.size == 1) byName[name] = same.single()
        }

        // Some apps cut a long name short ("Bonne Hotma…"): that still names the contact it starts.
        fun contactFor(entry: String): SearchResult? {
            val name = entry.trim()
            byName[name]?.let { return it }
            val cut = name.removeSuffix("\u2026").removeSuffix("...").trim()
            if (cut.length == name.length || cut.length < MIN_CUT) return null
            return byName.entries.filter { it.key.startsWith(cut) }.map { it.value }.singleOrNull()
        }

        val out = HashMap<String, String>()
        for (r in results) {
            val entry = r.foldedEntryLabel ?: continue
            if (r.kind != Kind.Shortcut || r.appLabel == null) continue
            contactFor(entry)?.let { out[r.id] = it.id }
        }
        return out
    }

    // Shorter than this, a cut label starts too many names to say which one it means.
    private const val MIN_CUT = 4
}
