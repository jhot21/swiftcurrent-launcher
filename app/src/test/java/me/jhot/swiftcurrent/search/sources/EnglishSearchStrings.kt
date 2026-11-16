package me.jhot.swiftcurrent.search.sources

/** Plain English for JVM tests; the real words are string resources, pinned by [SearchStringsTest]. */
object EnglishSearchStrings : SearchStrings {
    override val hiddenBadge = "Hidden"
    override val archivedBadge = "Archived"
    override val call = "Call"
    override val message = "Message"
    override val email = "Email"
    override fun callTo(what: String) = "Call $what"
    override fun messageTo(what: String) = "Message $what"
    override fun emailTo(what: String) = "Email $what"
}
