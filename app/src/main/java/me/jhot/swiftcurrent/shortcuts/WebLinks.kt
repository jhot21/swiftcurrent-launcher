package me.jhot.swiftcurrent.shortcuts

import android.content.Intent
import android.net.Uri
import java.util.Locale

/** A validated web address. [uri] is absolute and normalized, [display] is the address without its scheme. */
data class WebLink(val uri: String, val host: String, val display: String)

/**
 * Turns what the owner typed into a [WebLink], or null when it is not an http(s) address we will store.
 *
 * SECURITY: a link is opened by another app, so only http and https are accepted; every other scheme
 * (`javascript:`, `file:`, `content:`, `intent:`, custom) is refused, and so is anything with credentials
 * in it, which would otherwise land in search results and backups.
 */
object WebLinks {
    private val hasSchemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val hostAndPort = Regex("^[^/?#]*:\\d+(?:[/?#].*)?$")
    private val badHostChar = Regex("[\\s@<>\"\\\\]")

    fun parse(input: String): WebLink? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) return null
        // "example.com:8443" looks like a scheme but is a host and port.
        val explicit = hasSchemePrefix.containsMatchIn(text) && !hostAndPort.matches(text)
        val scheme = if (explicit) text.substringBefore(':').lowercase(Locale.ROOT) else "https"
        if (scheme != "http" && scheme != "https") return null
        val afterScheme = if (explicit) text.substringAfter(':') else text
        if (explicit && !afterScheme.startsWith("//")) return null
        val rest = if (explicit) afterScheme.removePrefix("//") else afterScheme
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val tail = rest.substring(authority.length)

        val host: String
        val port: String
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            host = authority.substring(0, close + 1)
            port = authority.substring(close + 1)
        } else {
            host = authority.substringBefore(':')
            port = authority.substring(host.length)
        }
        if (host.isEmpty() || host == "[]" || badHostChar.containsMatchIn(host)) return null
        if (!authority.startsWith("[") && ('[' in host || ']' in host)) return null
        if (port.isNotEmpty() && !(port.startsWith(":") && port.length > 1 && port.drop(1).all { it.isDigit() })) return null

        val lowerHost = host.lowercase(Locale.ROOT)
        val shownTail = if (tail == "/") "" else tail
        return WebLink(uri = "$scheme://$lowerHost$port$tail", host = lowerHost, display = "$lowerHost$port$shownTail")
    }

    fun defaultName(link: WebLink): String = link.host

    /** The intent stored for a web link: a VIEW of the address that only [browserPackage] may answer. */
    fun intentUri(link: WebLink, browserPackage: String): String =
        Intent(Intent.ACTION_VIEW, Uri.parse(link.uri))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(browserPackage)
            .toUri(Intent.URI_INTENT_SCHEME)
}
