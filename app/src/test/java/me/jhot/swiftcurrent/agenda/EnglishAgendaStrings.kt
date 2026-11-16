package me.jhot.swiftcurrent.agenda

/** Plain English for JVM tests; the real words are string resources. */
object EnglishAgendaStrings : AgendaStrings {
    override val today = "today"
    override val tomorrow = "tomorrow"
    override val noTitle = "(No title)"
    override val now = "now"
    override fun inMinutes(n: Long) = "in ${n}m"
    override fun inHours(n: Long) = "in ${n}h"
    override fun inDays(n: Long) = "in ${n}d"
    override fun nextLine(title: String, whenText: String) = "$title · $whenText"
    override fun nextLineMore(title: String, whenText: String, more: Int) = "$title · $whenText +$more"
    override fun detail(timeRange: String, relative: String) = "$timeRange · $relative"
}
