package me.jhot.swiftcurrent.shortcuts

import java.util.Locale

/** A Google Chat conversation: its [id] as Chat writes it, and the [link] Gmail's Chat deep link answers for it. */
data class ChatLink(val id: String, val link: WebLink)

/**
 * Reads the addresses Google Chat shows on the web and turns each into the one form the Android app
 * opens. Gmail and the Chat app only answer `chat.google.com/room/<id>` (a space) and `/dm/<id>`, not the
 * addresses a browser shows, so a pasted `mail.google.com/mail/u/0/#chat/space/<id>` or
 * `chat.google.com/app/chat/<id>` would open nothing.
 *
 * The id is the same string Gmail uses for the conversation's own shortcut, which is how a saved link can
 * be given the conversation's name without asking Google (the links carry no name).
 */
object ChatLinks {
    private val id = Regex("[A-Za-z0-9_-]{6,64}")

    // #chat/space/<id>, #chat/dm/<id>, #chat/room/<id> in the web client's fragment.
    private val fragment = Regex("^/?chat/(space|dm|room)/($ID)", RegexOption.IGNORE_CASE)
    private val path = Regex("^/(?:app/chat|room|dm|space)/($ID)", RegexOption.IGNORE_CASE)
    private val pathKind = Regex("^/(room|dm|space)/", RegexOption.IGNORE_CASE)
    private const val ID = "[A-Za-z0-9_-]{6,64}"

    fun parse(input: String): ChatLink? {
        val link = WebLinks.parse(input) ?: return null
        val rest = link.uri.substringAfter("://").substringAfter(link.host)
        val found: Pair<String?, String> = when (link.host) {
            "mail.google.com" -> {
                val frag = rest.substringAfter('#', "")
                fragment.find(frag)?.let { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] } ?: return null
            }
            "chat.google.com" -> {
                val p = rest.substringBefore('#').substringBefore('?')
                val m = path.find(p) ?: return null
                pathKind.find(p)?.groupValues?.get(1)?.lowercase(Locale.ROOT) to m.groupValues[1]
            }
            else -> return null
        }
        val (kind, conversationId) = found
        if (!id.matches(conversationId)) return null
        // A space id starts "AAAA"; a DM's does not. The web's /app/chat/<id> does not say which it is.
        val space = kind == "space" || kind == "room" || (kind == null && conversationId.startsWith("AAAA"))
        val route = if (space) "room" else "dm"
        return ChatLink(conversationId, WebLink("https://chat.google.com/$route/$conversationId", "chat.google.com", "chat.google.com/$route/$conversationId"))
    }
}
