package me.jhot.swiftcurrent.core

/** Renaming favorites, pop-up items and folders; pure so the rules are tested without Android. */
object Renames {
    /** Longest custom label kept; a longer one is cut, never rejected. */
    const val MAX_LENGTH = 40

    /** What a text field may hold while typing: no control characters, at most [MAX_LENGTH]; spaces are kept. */
    fun limit(raw: String): String {
        val clean = raw.filterNot { it.isISOControl() || it == '\u2028' || it == '\u2029' }
        var end = minOf(clean.length, MAX_LENGTH)
        // Never end on half of a surrogate pair.
        if (end < clean.length && end > 0 && Character.isHighSurrogate(clean[end - 1])) end--
        return clean.substring(0, end)
    }

    /** The label as it will be stored: stripped, trimmed, cut. Null means "use the default". */
    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        return limit(raw.filterNot { it.isISOControl() || it == '\u2028' || it == '\u2029' }.trim()).trim().ifEmpty { null }
    }

    private fun children(host: Entry): List<Entry> =
        host.popup?.rows?.filterIsInstance<PopupRow.Item>()?.map { it.entry }.orEmpty()

    private fun nested(host: Entry, id: String): Entry? =
        children(host).firstNotNullOfOrNull { if (it.id == id) it else nested(it, id) }

    /**
     * The owner's name for the app [component], or null for the system name: the stored per-app name,
     * else a name an older version kept on a favorite entry (top level first, then inside pop-ups).
     */
    fun appNameOf(data: LauncherData, component: String): String? {
        data.appNames[component]?.ifBlank { null }?.let { return it }
        fun walk(entries: List<Entry>): String? = entries.firstNotNullOfOrNull { e ->
            if ((e.target as? Target.App)?.component == component) e.customLabel?.ifBlank { null } else null
        } ?: entries.firstNotNullOfOrNull { e -> e.popup?.rows?.filterIsInstance<PopupRow.Item>()?.map { it.entry }?.let(::walk) }
        return walk(data.favorites) ?: walk(data.folders)
    }

    /**
     * Gives the app [component] one name (null or blank restores the system name) and drops the older
     * per-entry copies of it, wherever the app appears, so there is a single place the name lives and no
     * two places can disagree. Returns [data] itself when nothing changes.
     */
    fun setAppName(data: LauncherData, component: String, label: String?): LauncherData {
        val l = sanitize(label)
        val names = if (l == null) data.appNames - component else data.appNames + (component to l)
        fun isIt(e: Entry) = (e.target as? Target.App)?.component == component
        fun clean(e: Entry): Entry {
            val p = e.popup
            val rows = p?.rows?.map { if (it is PopupRow.Item) PopupRow.Item(clean(it.entry)) else it }
            val inner = if (p == null) e else e.copy(popup = p.copy(rows = rows!!))
            return if (isIt(inner) && inner.customLabel != null) inner.copy(customLabel = null) else inner
        }
        val favorites = data.favorites.map(::clean)
        val folders = data.folders.map(::clean)
        val appPopups = data.appPopups.mapValues { (c, popup) -> clean(Entry(c, Target.App(c), popup = popup)).popup ?: popup }
        return if (names == data.appNames && favorites == data.favorites && folders == data.folders && appPopups == data.appPopups) data
        else data.copy(appNames = names, favorites = favorites, folders = folders, appPopups = appPopups)
    }

    /**
     * The hosts a pop-up can belong to: favorites, the folder library, and an app that is no favorite, whose
     * pop-up is kept in [LauncherData.appPopups] and is held here as an entry of its own so the same rules reach it.
     */
    private fun appHosts(data: LauncherData): List<Entry> =
        data.appPopups.map { (component, popup) -> Entry(component, Target.App(component), popup = popup) }

    private fun findEntry(data: LauncherData, entryId: String, within: String?, isFolder: Boolean?): Entry? {
        val all = data.favorites + data.folders + appHosts(data)
        fun matches(e: Entry) = e.id == entryId && when (isFolder) {
            true -> e.target is Target.Folder
            false -> e.target !is Target.Folder
            null -> true
        }
        if (within != null) return all.firstOrNull { it.id == within }?.let { host -> children(host).firstOrNull(::matches) }
        // Only a favorite or a library folder is found by its own id; an app's pop-up host is only somewhere to look inside.
        return (data.favorites + data.folders).firstOrNull(::matches) ?: all.firstNotNullOfOrNull { host -> nestedMatching(host, ::matches) }
    }

    private fun nestedMatching(host: Entry, match: (Entry) -> Boolean): Entry? =
        children(host).firstNotNullOfOrNull { if (match(it)) it else nestedMatching(it, match) }

    /** The custom label currently stored for [entryId], in the same place [apply] would change it. */
    fun customLabelOf(data: LauncherData, entryId: String, within: String? = null): String? {
        // An app has one name, wherever it appears.
        (findEntry(data, entryId, within, isFolder = null)?.target as? Target.App)?.let { return appNameOf(data, it.component) }
        val all = data.favorites + data.folders
        if (within != null) {
            return (all + appHosts(data)).firstOrNull { it.id == within }?.let { host -> children(host).firstOrNull { it.id == entryId } }?.customLabel?.ifBlank { null }
        }
        all.firstOrNull { it.id == entryId }?.let { return it.customLabel?.ifBlank { null } }
        return (all + appHosts(data)).firstNotNullOfOrNull { nested(it, entryId) }?.customLabel?.ifBlank { null }
    }

    /**
     * Sets or clears the custom label of [entryId]. A folder is one thing wherever it shows, so a
     * folder is renamed in the library, in favorites and in every pop-up that holds a copy. Any other
     * entry is renamed where it is a favorite; with [within] set it is the item inside that host's
     * pop-up; an entry that is only ever inside a pop-up is renamed there. Ids, targets and pop-ups
     * are never touched, and a rename to the stored value returns [data] unchanged. [isFolder] tells a
     * folder from an app that shares its id (null matches either).
     */
    fun apply(data: LauncherData, entryId: String, label: String?, within: String? = null, isFolder: Boolean? = null): LauncherData {
        // An app has one name for all its appearances; only shortcuts and folders are named per entry.
        (findEntry(data, entryId, within, isFolder)?.target as? Target.App)?.let { return setAppName(data, it.component, label) }
        val l = sanitize(label)

        // A stored blank means "no label", so naming it blank again changes nothing.
        fun Entry.named() = if ((customLabel?.ifBlank { null }) == l) this else copy(customLabel = l)

        // A folder and an app may share an id; the caller says which one it means (null: either).
        fun isItem(e: Entry) = e.target !is Target.Folder && e.id == entryId && isFolder != true
        fun isTheFolder(e: Entry) = e.target is Target.Folder && e.id == entryId && isFolder != false

        val topLevelItem = within == null && data.favorites.any(::isItem)

        // Bottom-up over a host and everything nested in its pop-up.
        fun visit(e: Entry, top: Boolean): Entry {
            val p = e.popup
            val rows = p?.rows?.map { if (it is PopupRow.Item) PopupRow.Item(visit(it.entry, top = false)) else it }
            var out = if (p == null) e else e.copy(popup = p.copy(rows = rows!!))
            if (isTheFolder(out)) return out.named()
            when {
                within != null -> if (out.id == within && out.popup != null) {
                    out = out.copy(
                        popup = out.popup!!.copy(
                            rows = out.popup!!.rows.map {
                                if (it is PopupRow.Item && isItem(it.entry)) PopupRow.Item(it.entry.named()) else it
                            },
                        ),
                    )
                }
                topLevelItem -> if (top && isItem(out)) out = out.named()
                else -> if (isItem(out)) out = out.named()
            }
            return out
        }

        val favorites = data.favorites.map { visit(it, top = true) }
        val folders = data.folders.map { visit(it, top = false) }
        val appPopups = data.appPopups.mapValues { (component, popup) ->
            visit(Entry(component, Target.App(component), popup = popup), top = false).popup ?: popup
        }
        return if (favorites == data.favorites && folders == data.folders && appPopups == data.appPopups) data
        else data.copy(favorites = favorites, folders = folders, appPopups = appPopups)
    }
}
