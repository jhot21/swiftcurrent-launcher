package me.jhot.swiftcurrent.search.sources

import android.content.res.Resources
import me.jhot.swiftcurrent.R

/**
 * The words the search sources put on results. The sources stay free of Android resources: the shell
 * builds this from string resources, and JVM tests pass a plain-English fake pinned to them by a test.
 */
interface SearchStrings {
    val hiddenBadge: String
    val archivedBadge: String
    val call: String
    val message: String
    val email: String

    /** "Call mobile": [what] says which number or address when a contact has several. */
    fun callTo(what: String): String
    fun messageTo(what: String): String
    fun emailTo(what: String): String
}

class ResourceSearchStrings(private val res: Resources) : SearchStrings {
    override val hiddenBadge get() = res.getString(R.string.search_badge_hidden)
    override val archivedBadge get() = res.getString(R.string.search_badge_archived)
    override val call get() = res.getString(R.string.contact_call)
    override val message get() = res.getString(R.string.contact_message)
    override val email get() = res.getString(R.string.contact_email)
    override fun callTo(what: String) = res.getString(R.string.contact_call_to, what)
    override fun messageTo(what: String) = res.getString(R.string.contact_message_to, what)
    override fun emailTo(what: String) = res.getString(R.string.contact_email_to, what)
}
