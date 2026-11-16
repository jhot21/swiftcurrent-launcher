package me.jhot.swiftcurrent.agenda

import me.jhot.swiftcurrent.search.LaunchOutcome
import java.net.URLEncoder

/** Plain description of an intent, so callers are testable without Android; the shell turns it into an Intent. */
data class LocationIntentSpec(val action: String, val uri: String)

/** What tapping an event's location does: a link opens as one, anything else is searched for in a maps app. */
object LocationIntents {
    private const val VIEW = "android.intent.action.VIEW"
    private val url = Regex("""https?://\S+""")
    private val whitespace = Regex("""\s+""")

    /** In the order to try them: the first that a phone can open wins. Empty for a blank location. */
    fun specs(location: String): List<LocationIntentSpec> {
        val text = location.trim().replace(whitespace, " ")
        if (text.isEmpty()) return emptyList()
        url.find(text)?.let { return listOf(LocationIntentSpec(VIEW, it.value.trimEnd('.', ',', ';', ')', ']'))) }
        // URLEncoder writes a space as "+", which a geo: query would show literally.
        val query = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        // A geo: query opens whichever maps app the phone has (Waze among them); the web page is for a phone with none.
        return listOf(
            LocationIntentSpec(VIEW, "geo:0,0?q=$query"),
            LocationIntentSpec(VIEW, "https://www.google.com/maps/search/?api=1&query=$query"),
        )
    }

    /** Tries each spec until one launches; when none does, the last failure; nothing to try counts as launched. */
    fun perform(specs: List<LocationIntentSpec>, launch: (LocationIntentSpec) -> LaunchOutcome): LaunchOutcome {
        // No specs means nothing to open, and nothing that failed.
        var last: LaunchOutcome = LaunchOutcome.Launched
        for (spec in specs) {
            last = launch(spec)
            if (last is LaunchOutcome.Launched) return last
        }
        return last
    }
}
