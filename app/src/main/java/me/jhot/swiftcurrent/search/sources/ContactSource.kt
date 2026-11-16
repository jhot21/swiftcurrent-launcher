package me.jhot.swiftcurrent.search.sources

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.ContactsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.search.Action
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.SearchResult
import me.jhot.swiftcurrent.search.SearchSource
import me.jhot.swiftcurrent.search.sources.ContactActionKey.Method

/**
 * [rowId] is the `Data._ID` of the row, what a data-row launch (a connected app) needs; 0 for the marker row.
 * [typeLabel] is the kind of number or address as the phone words it ("Mobile", "Work"), null when it has none.
 * [appName] and [text] are set only on a connected app's row: the app that opens it ("Signal") and the row's
 * own words ("Message +1 555..."). Null on every other row.
 */
data class ContactRow(
    val contactId: Long,
    val name: String,
    val mimetype: String,
    val data: String,
    val rowId: Long = 0,
    val typeLabel: String? = null,
    val appName: String? = null,
    val appPackage: String? = null,
    val text: String? = null,
)

/** Blocking provider query: callers must run it off the main thread (ContactSource does). */
interface ContactReader {
    fun rows(query: String): List<ContactRow>

    /** As [rows], but cancelling [signal] stops the provider query that is running, not just the wait for it. */
    fun rows(query: String, signal: CancellationSignal): List<ContactRow> = rows(query)

    /** Every action row of these contacts, plus a marker row for one that has none; a gone contact has no rows. */
    fun rowsForContacts(ids: Set<Long>): List<ContactRow>

    fun rowsForContacts(ids: Set<Long>, signal: CancellationSignal): List<ContactRow> = rowsForContacts(ids)
}

/**
 * Plain description of an intent, so the source is testable without Android. [packageName] aims the launch at one
 * app, so no other viewer of that type is offered beside it.
 */
data class ContactIntentSpec(val action: String, val uri: String, val mimetype: String? = null, val packageName: String? = null)

fun interface ContactActionFactory {
    fun launch(spec: ContactIntentSpec): LaunchOutcome
}

private const val PHONE = "vnd.android.cursor.item/phone_v2"
private const val EMAIL = "vnd.android.cursor.item/email_v2"
private const val ITEM_PREFIX = "vnd.android.cursor.item/"

/** How long a typed query waits before the provider is asked, so keys typed in a burst ask it once. */
private const val DEBOUNCE_MS = 50L

/**
 * The kinds Android defines itself. Every other row under [ITEM_PREFIX] might belong to a connected app: they are
 * not all `vnd.`-prefixed (Meet's is `com.google.android.apps.tachyon.phone`), so the app that opens one decides.
 */
private val STANDARD_KINDS = setOf(
    "name", "nickname", "photo", "note", "organization", "website", "im", "relation", "contact_event",
    "postal-address_v2", "sip_address", "group_membership", "identity",
).mapTo(HashSet()) { ITEM_PREFIX + it }

class ContactSource(
    private val reader: ContactReader,
    private val access: () -> Boolean,
    private val strings: SearchStrings,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val actionFactory: ContactActionFactory,
) : SearchSource {
    override val name = "Contacts"

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank() || !access()) return emptyList()
        // Each keystroke replaces this search, so one that is replaced inside this wait never reaches the provider.
        delay(DEBOUNCE_MS)
        val rows = cancellable { reader.rows(query.trim(), it) }
        return rows.groupBy { it.contactId }.map { (id, all) -> contact(id, all).result }
    }

    /**
     * Runs a blocking provider query on [dispatcher]. Cancelling the coroutine cancels [CancellationSignal]
     * too: the provider query itself stops, instead of running to the end for an answer nobody reads.
     */
    private suspend fun <T> cancellable(query: (CancellationSignal) -> T): T = withContext(dispatcher) {
        val signal = CancellationSignal()
        val work = async { query(signal) }
        try {
            work.await()
        } catch (e: CancellationException) {
            signal.cancel()
            throw e
        }
    }

    override suspend fun resolve(ids: Set<String>): List<SearchResult> {
        if (!access()) return emptyList()
        val wanted = ids.mapNotNull { id -> parseContactId(id)?.let { id to it } }
        if (wanted.isEmpty()) return emptyList()
        // Several method keys can name one contact: one provider query covers them all.
        val contactIds = wanted.mapTo(HashSet()) { (_, ref) -> ref.contactId }
        val byContact = cancellable { reader.rowsForContacts(contactIds, it) }.groupBy { it.contactId }
        val built = HashMap<Long, Contact>()
        return wanted.mapNotNull { (id, ref) ->
            val all = byContact[ref.contactId] ?: return@mapNotNull null
            val contact = built.getOrPut(ref.contactId) { contact(ref.contactId, all) }
            // Recents rows are plain tap targets: never folded, never listing the ways to reach the contact.
            if (ref.method == null) return@mapNotNull contact.result.copy(secondary = emptyList())
            // An app action is named exactly by its key (mimetype and data), so a sibling never answers for it.
            // A phone or email key holds the raw data of whichever row was kept when it was made; matching every
            // raw row keeps it live when the provider now lists that number in another order or spelling.
            val action = contact.methods.firstOrNull { it.key == id } ?: run {
                if (ref.method == Method.App) return@mapNotNull null
                val raw = all.firstOrNull { it.mimetype == mimetypeOf(ref.method) && ContactActionKey.of(ref.contactId, ref.method, it.data) == id }
                    ?: return@mapNotNull null
                contact.methods.firstOrNull { it.method == ref.method && it.row.mimetype == raw.mimetype && normalized(it.row) == normalized(raw) }
                    ?: return@mapNotNull null
            }
            SearchResult(
                id = id,
                label = "${action.action.label}: ${contact.result.label}",
                sourceName = name,
                kind = Kind.Contact,
                icon = null,
                primary = action.action.copy(recentId = id),
                ownerId = contact.result.id,
            )
        }
    }

    private class MethodAction(val method: Method, val row: ContactRow, val key: String, val action: Action)
    private class Contact(val result: SearchResult, val methods: List<MethodAction>)

    /** The one place a contact's row and its actions are built, so search and recents label them alike. */
    private fun contact(id: Long, all: List<ContactRow>): Contact {
        val group = all.distinctBy { Triple(it.mimetype, normalized(it), it.text) }
        val label = group.first().name
        fun act(label: String, spec: ContactIntentSpec, recentId: String? = null) =
            Action(label, null, recentId) { actionFactory.launch(spec) }
        fun MutableList<MethodAction>.add(method: Method, row: ContactRow, label: String, spec: ContactIntentSpec) =
            ContactActionKey.of(id, method, row.data).let { key -> add(MethodAction(method, row, key, act(label, spec, key))) }
        // With several numbers or addresses a bare "Call" says nothing, so each is named by its kind or value.
        val phoneTags = tagsFor(group.filter { it.mimetype == PHONE })
        val emailTags = tagsFor(group.filter { it.mimetype == EMAIL })
        val methods = buildList {
            for (row in group) when (row.mimetype) {
                PHONE -> {
                    val tag = phoneTags[row]
                    add(Method.Call, row, tag?.let(strings::callTo) ?: strings.call, ContactIntentSpec(Intent.ACTION_DIAL, "tel:${encodeNumber(row.data)}"))
                    add(Method.Message, row, tag?.let(strings::messageTo) ?: strings.message, ContactIntentSpec(Intent.ACTION_SENDTO, "smsto:${encodeNumber(row.data)}"))
                }
                EMAIL -> add(Method.Email, row, emailTags[row]?.let(strings::emailTo) ?: strings.email, ContactIntentSpec(Intent.ACTION_SENDTO, "mailto:${row.data}"))
            }
            // A connected app's row (Signal, WhatsApp, ...) opens through its own content URI: its DATA1 is
            // whatever the app stores (a number, a JID), never a URI. Grouped by app, in the order first seen.
            val connected = group.filter { it.appName != null }
            val repeated = connected.groupingBy { it.mimetype }.eachCount().filterValues { it > 1 }.keys
            val ordered = connected.groupBy { it.appName }.values.flatten()
            val labels = distinguishLabels(
                ordered.map { row ->
                    connectedActionLabel(row.appName!!, row.text, row.data)
                        .let { if (row.mimetype in repeated) "$it ${connectedActionNumber(row.text, row.data)}" else it }
                },
                ordered.map { it.mimetype },
            )
            ordered.forEachIndexed { i, row ->
                val key = ContactActionKey.ofApp(id, row.mimetype, row.data)
                val spec = ContactIntentSpec(Intent.ACTION_VIEW, "content://com.android.contacts/data/${row.rowId}", row.mimetype, row.appPackage)
                add(MethodAction(Method.App, row, key, act(labels[i], spec, key)))
            }
        }
        val result = SearchResult(
            id = "contact:$id",
            label = label,
            sourceName = name,
            kind = Kind.Contact,
            icon = null,
            primary = act(label, ContactIntentSpec(Intent.ACTION_VIEW, "content://com.android.contacts/contacts/$id")),
            secondary = methods.map { it.action },
        )
        return Contact(result, methods)
    }
}

/** A stored contact id: the contact itself ([method] null) or one way to reach it. */
private class ContactRef(val contactId: Long, val method: Method?)

private fun parseContactId(id: String): ContactRef? {
    ContactActionKey.parse(id)?.let { return ContactRef(it.contactId, it.method) }
    if (!id.startsWith("contact:")) return null
    return id.removePrefix("contact:").toLongOrNull()?.let { ContactRef(it, null) }
}

private fun mimetypeOf(method: Method) = when (method) {
    Method.Call, Method.Message -> PHONE
    Method.Email -> EMAIL
    Method.App -> error("an app key carries its own mimetype")
}

/**
 * What tells these rows apart: nothing for a single row, else each one's kind ("mobile") when every
 * kind is different, else its number or address.
 */
private fun tagsFor(rows: List<ContactRow>): Map<ContactRow, String> {
    if (rows.size < 2) return emptyMap()
    val kinds = rows.map { it.typeLabel?.trim()?.lowercase().orEmpty() }
    val distinctKinds = kinds.all { it.isNotEmpty() } && kinds.toSet().size == kinds.size
    return rows.associateWith { if (distinctKinds) it.typeLabel!!.trim().lowercase() else it.data.trim() }
}

/** Two rows are the same action when they have the same kind and the same number or address, however it is written. */
private fun normalized(row: ContactRow): String = when (row.mimetype) {
    PHONE -> row.data.filter { it.isDigit() || it == '+' || it == '#' || it == '*' }
    EMAIL -> row.data.trim().lowercase()
    else -> row.data
}

/** Percent-encodes a number for a uri, keeping `+`: what `Uri.encode(number, "+")` does, usable on the JVM. */
private fun encodeNumber(number: String): String = buildString {
    for (byte in number.toByteArray(Charsets.UTF_8)) {
        val c = (byte.toInt() and 0xFF).toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "_-!.~'()*+") append(c) else append("%%%02X".format(byte))
    }
}

/**
 * Two steps so the limit is real and a contact with no phone, email or connected-app row still appears:
 * first the matching contacts (limited by the provider), then their action rows.
 */
class ContentResolverContactReader(
    private val context: Context,
    private val apps: ConnectedAppResolver,
    private val limit: Int = 10,
) : ContactReader {
    private val resolver: ContentResolver = context.contentResolver

    override fun rows(query: String): List<ContactRow> = rowsOf(query, null)

    override fun rows(query: String, signal: CancellationSignal): List<ContactRow> = rowsOf(query, signal)

    override fun rowsForContacts(ids: Set<Long>): List<ContactRow> = rowsOfContacts(ids, null)

    override fun rowsForContacts(ids: Set<Long>, signal: CancellationSignal): List<ContactRow> = rowsOfContacts(ids, signal)

    private fun rowsOf(query: String, signal: CancellationSignal?): List<ContactRow> = try {
        read(query, signal)
    } catch (e: SecurityException) {
        emptyList() // permission revoked mid-query
    }

    private fun rowsOfContacts(ids: Set<Long>, signal: CancellationSignal?): List<ContactRow> = try {
        if (ids.isEmpty()) emptyList() else readContacts(ids, signal)
    } catch (e: SecurityException) {
        emptyList() // permission revoked mid-query
    }

    // DATA2 is the kind (mobile, work, ...) and DATA3 the owner's own name for a custom kind.
    private fun typeLabelOf(mimetype: String, c: android.database.Cursor): String? {
        if (c.isNull(4)) return null
        val type = c.getInt(4)
        val custom = c.getString(5)
        return when (mimetype) {
            PHONE -> ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, custom)
            EMAIL -> ContactsContract.CommonDataKinds.Email.getTypeLabel(context.resources, type, custom)
            else -> null
        }?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun read(query: String, signal: CancellationSignal?): List<ContactRow> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val names = LinkedHashMap<Long, String>()
        val contactArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? ESCAPE '\\'",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%$escaped%"))
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC, ${ContactsContract.Contacts._ID} ASC",
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            contactArgs,
            signal,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                if (!name.isNullOrBlank()) names[c.getLong(0)] = name
            }
        }
        return rowsFor(names, signal)
    }

    private fun readContacts(contactIds: Set<Long>, signal: CancellationSignal?): List<ContactRow> {
        val names = LinkedHashMap<Long, String>()
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${ContactsContract.Contacts._ID} IN (${contactIds.joinToString(",") { "?" }})",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, contactIds.map { it.toString() }.toTypedArray())
        }
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            args,
            signal,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                if (!name.isNullOrBlank()) names[c.getLong(0)] = name
            }
        }
        return rowsFor(names, signal)
    }

    /** The action rows of [names]' contacts, with a marker row for a contact that has none. */
    private fun rowsFor(names: Map<Long, String>, signal: CancellationSignal?): List<ContactRow> {
        if (names.isEmpty()) return emptyList()

        val ids = names.keys.toList()
        val placeholders = ids.joinToString(",") { "?" }
        val dataArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${ContactsContract.Data.CONTACT_ID} IN ($placeholders) AND " +
                    "(${ContactsContract.Data.MIMETYPE} IN (?, ?) OR ${ContactsContract.Data.MIMETYPE} LIKE ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                (ids.map { it.toString() } + listOf(PHONE, EMAIL, "$ITEM_PREFIX%")).toTypedArray(),
            )
        }
        val byContact = HashMap<Long, MutableList<ContactRow>>()
        // One lookup per kind of row for this read: the owning app is the same for every contact's row of it.
        val appsByMimetype = HashMap<String, ConnectedApp?>()
        fun appOf(mimetype: String, rowId: Long): ConnectedApp? {
            if (!appsByMimetype.containsKey(mimetype)) appsByMimetype[mimetype] = try { apps.appFor(mimetype, rowId) } catch (e: RuntimeException) { null }
            return appsByMimetype[mimetype]
        }
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data._ID,
                ContactsContract.Data.DATA2, ContactsContract.Data.DATA3, ContactsContract.Data.DATA4,
            ),
            dataArgs,
            signal,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = names[id] ?: continue
                val mimetype = c.getString(1) ?: continue
                val data = c.getString(2)
                if (data.isNullOrBlank()) continue
                val rowId = c.getLong(3)
                val row = if (mimetype == PHONE || mimetype == EMAIL) {
                    ContactRow(id, name, mimetype, data, rowId, typeLabelOf(mimetype, c))
                } else {
                    if (mimetype in STANDARD_KINDS) continue
                    val app = appOf(mimetype, rowId) ?: continue
                    ContactRow(id, name, mimetype, data, rowId, appName = app.name, appPackage = app.packageName, text = (c.getString(5) ?: c.getString(6) ?: c.getString(4))?.takeIf { it.isNotBlank() })
                }
                byContact.getOrPut(id) { mutableListOf() } += row
            }
        }
        // A contact with no action rows still gets a marker row, so it shows up without buttons.
        return names.flatMap { (id, name) ->
            byContact[id] ?: listOf(ContactRow(id, name, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, name))
        }
    }
}
