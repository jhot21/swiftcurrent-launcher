package me.jhot.swiftcurrent.search.sources

/**
 * The recents identity of one way to reach a contact: `contact:<contactId>|<method>|<data>`, where
 * data is the row's number, address or WhatsApp JID. Call and Message share a number, so the method
 * is part of the key. A connected app's row is `contact:<id>|app|<mimetype>|<data>`: the mimetype is what
 * tells Signal's message, voice and video rows apart. Kept as a plain string so it is stored as-is beside
 * the contact's own id.
 */
object ContactActionKey {
    /** Named so it is not confused with search's `Kind`. */
    enum class Method { Call, Message, Email, App }

    /** [mimetype] is set exactly for [Method.App]. */
    data class Parsed(val contactId: Long, val method: Method, val data: String, val mimetype: String? = null) {
        init {
            require((method == Method.App) == (mimetype != null)) { "only an app key has a mimetype" }
        }
    }

    private const val PREFIX = "contact:"

    /** WhatsApp was a method of its own before every app was generic; its stored keys must keep working. */
    private const val WHATSAPP = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
    private const val LEGACY_WHATSAPP = "whatsapp"
    private val byName = Method.entries.associateBy { it.name.lowercase() }

    fun of(contactId: Long, method: Method, data: String): String {
        require(method != Method.App) { "an app key needs a mimetype: use ofApp" }
        return "$PREFIX$contactId|${method.name.lowercase()}|$data"
    }

    fun ofApp(contactId: Long, mimetype: String, data: String): String =
        if (mimetype == WHATSAPP) "$PREFIX$contactId|$LEGACY_WHATSAPP|$data"
        else "$PREFIX$contactId|${Method.App.name.lowercase()}|$mimetype|$data"

    fun parse(key: String): Parsed? {
        if (!key.startsWith(PREFIX)) return null
        // A limit of 3 keeps any `|` inside the data with the data.
        val parts = key.substring(PREFIX.length).split('|', limit = 3)
        if (parts.size != 3) return null
        val id = parts[0].toLongOrNull() ?: return null
        if (parts[2].isEmpty()) return null
        if (parts[1] == LEGACY_WHATSAPP) return Parsed(id, Method.App, parts[2], WHATSAPP)
        val method = byName[parts[1]] ?: return null
        if (method != Method.App) return Parsed(id, method, parts[2])
        val (mimetype, data) = parts[2].split('|', limit = 2).takeIf { it.size == 2 } ?: return null
        if (mimetype.isEmpty() || data.isEmpty()) return null
        return Parsed(id, method, data, mimetype)
    }
}
