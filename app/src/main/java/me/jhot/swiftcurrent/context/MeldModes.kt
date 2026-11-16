package me.jhot.swiftcurrent.context

import android.content.ContentResolver
import android.net.Uri

/**
 * Meld's modes, read from its provider. Null means unknown (Meld missing, permission denied, provider not
 * started), never "no modes". Blocking IPC: call off the main thread.
 */
interface MeldModes {
    /** Every non-default mode name. */
    fun modes(): List<String>?

    /** The modes in effect; empty when none. */
    fun active(): List<String>?
}

object NoMeldModes : MeldModes {
    override fun modes(): List<String>? = null
    override fun active(): List<String>? = null
}

class DefaultMeldModes(private val resolver: ContentResolver) : MeldModes {
    override fun modes() = names(MODES_URI)
    override fun active() = names(ACTIVE_URI)

    // Meld's contract: a null cursor or an exception means it could not be asked, which must stay
    // unknown rather than read as an empty set.
    private fun names(uri: Uri): List<String>? = try {
        resolver.query(uri, arrayOf(COLUMN_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndexOrThrow(COLUMN_NAME)
            buildList { while (cursor.moveToNext()) cursor.getString(column)?.let(::add) }
        }
    } catch (e: RuntimeException) {
        null
    }

    companion object {
        const val PACKAGE = "me.jhot.meld"
        const val PERMISSION = "me.jhot.meld.permission.READ_MODES"
        const val ACTION_MODES_CHANGED = "meld.intent.MODES_CHANGED"
        private const val COLUMN_NAME = "name"
        private val MODES_URI: Uri = Uri.parse("content://me.jhot.meld.state/modes")
        private val ACTIVE_URI: Uri = Uri.parse("content://me.jhot.meld.state/active")
    }
}
