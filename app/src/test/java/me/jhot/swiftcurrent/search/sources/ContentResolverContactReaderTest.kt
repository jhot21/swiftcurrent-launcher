package me.jhot.swiftcurrent.search.sources

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

private const val PHONE = "vnd.android.cursor.item/phone_v2"
private const val NAME_ITEM = "vnd.android.cursor.item/name"
private const val SIGNAL_MSG = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
private const val SIGNAL_CALL = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"
private const val SIGNAL_VIDEO = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.videocall"

/** Serves just enough of the contacts provider to see what the reader asks for. */
class FakeContactsProvider : ContentProvider() {
    data class Person(val id: Long, val name: String)
    data class DataRow(val contactId: Long, val mimetype: String, val data: String?, val rowId: Long = contactId * 100, val type: Int? = null, val customLabel: String? = null, val data4: String? = null)

    var people = listOf<Person>()
    var data = listOf<DataRow>()
    var denied = false
    var contactsLimit: Int? = null
    var contactsArgs: List<String> = emptyList()
    var contactsSelection: String? = null
    var contactsServed = 0
    var dataServed = 0
    var dataRequestedIds: Set<Long> = emptySet()
    var contactsQueries = 0
    var dataQueries = 0
    val signals = mutableListOf<CancellationSignal?>()

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        error("the reader must use the Bundle form so the limit reaches the provider")

    override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor? {
        if (denied) throw SecurityException("READ_CONTACTS revoked")
        signals += cancellationSignal
        val args = queryArgs?.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)?.toList().orEmpty()
        val limit = queryArgs?.getInt(ContentResolver.QUERY_ARG_LIMIT, -1)?.takeIf { it >= 0 }
        return if (uri.path == "/contacts" && queryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)?.contains(" IN (") == true) {
            contactsQueries++
            val ids = args.mapNotNull { it.toLongOrNull() }.toSet()
            val hits = people.filter { it.id in ids }
            contactsServed += hits.size
            MatrixCursor(arrayOf("_id", "display_name")).also { c -> hits.forEach { c.addRow(arrayOf<Any?>(it.id, it.name)) } }
        } else if (uri.path == "/contacts") {
            contactsQueries++
            contactsLimit = limit
            contactsArgs = args
            contactsSelection = queryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)
            val pattern = likeRegex(args.first())
            val hits = people.filter { pattern.matches(it.name) }.sortedWith(compareBy({ it.name }, { it.id }))
                .let { if (limit != null) it.take(limit) else it }
            contactsServed += hits.size
            MatrixCursor(arrayOf("_id", "display_name")).also { c -> hits.forEach { c.addRow(arrayOf<Any?>(it.id, it.name)) } }
        } else {
            val ids = args.mapNotNull { it.toLongOrNull() }.toSet()
            val nonIds = args.filter { it.toLongOrNull() == null }
            val types = nonIds.filterNot { it.endsWith("%") }.toSet()
            val prefixes = nonIds.filter { it.endsWith("%") }.map { it.removeSuffix("%") }
            dataRequestedIds = ids
            dataQueries++
            val hits = data.filter { it.contactId in ids && (it.mimetype in types || prefixes.any { p -> it.mimetype.startsWith(p) }) }
            dataServed += hits.size
            MatrixCursor(arrayOf("contact_id", "mimetype", "data1", "_id", "data2", "data3", "data4")).also { c ->
                hits.forEach { c.addRow(arrayOf<Any?>(it.contactId, it.mimetype, it.data, it.rowId, it.type, it.customLabel, it.data4)) }
            }
        }
    }

    /** SQL LIKE with `\` as the escape character, as the reader declares it. */
    private fun likeRegex(like: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < like.length) {
            when (val ch = like[i]) {
                '\\' -> { i++; sb.append(Regex.escape(like[i].toString())) }
                '%' -> sb.append(".*")
                '_' -> sb.append(".")
                else -> sb.append(Regex.escape(ch.toString()))
            }
            i++
        }
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
class ContentResolverContactReaderTest {
    private lateinit var provider: FakeContactsProvider
    private lateinit var reader: ContentResolverContactReader

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeContactsProvider::class.java).create(ContactsContract.AUTHORITY).get()
        reader = ContentResolverContactReader(ApplicationProvider.getApplicationContext<Context>(), resolver)
    }

    /** What the installed apps can open: mimetype -> the app's name. */
    private val apps = mutableMapOf<String, String>()
    private val resolved = mutableListOf<String>()
    private var resolverThrows = false
    private val resolver = ConnectedAppResolver { mimetype, _ ->
        resolved += mimetype
        if (resolverThrows) throw IllegalStateException("package manager died")
        apps[mimetype]?.let { ConnectedApp(it, "pkg.${it.lowercase()}") }
    }

    private fun person(id: Long, name: String) = FakeContactsProvider.Person(id, name)

    @Test
    fun a_signal_given_to_the_reader_reaches_every_provider_query() {
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(FakeContactsProvider.DataRow(1, "vnd.android.cursor.item/phone_v2", "+15550100"))
        reader.rows("alex", CancellationSignal())
        // One for the contact, one for its rows: both must be cancellable, or a stale query runs to the end.
        assertEquals(2, provider.signals.size)
        assertTrue(provider.signals.all { it != null })
    }

    @Test
    fun reader_returns_a_name_only_contact_with_no_rows_so_it_shows_no_buttons() {
        provider.people = listOf(person(1, "Alex Example"))
        val rows = reader.rows("alex")
        assertEquals(listOf(1L), rows.map { it.contactId })
        val result = kotlinx.coroutines.runBlocking {
            ContactSource(reader, { true }, EnglishSearchStrings, actionFactory = { me.jhot.swiftcurrent.search.LaunchOutcome.Launched }).search("alex")
        }.single()
        assertEquals("Alex Example", result.label)
        assertTrue(result.secondary.isEmpty())
    }

    @Test
    fun reader_caps_at_ten_contacts_without_reading_the_rest() {
        provider.people = (1L..50L).map { person(it, "Alex Example %02d".format(it)) }
        provider.data = (1L..50L).map { FakeContactsProvider.DataRow(it, PHONE, "+1555010$it") }
        val rows = reader.rows("a")
        assertEquals(10, rows.map { it.contactId }.toSet().size)
        assertEquals(10, provider.contactsLimit)
        assertEquals(10, provider.contactsServed)
        assertEquals(10, provider.dataServed)
        assertEquals((1L..10L).toSet(), provider.dataRequestedIds)
    }

    @Test
    fun reader_skips_blank_and_null_data_rows_without_consuming_a_slot() {
        provider.people = (1L..12L).map { person(it, "Alex Example %02d".format(it)) }
        provider.data = listOf(
            FakeContactsProvider.DataRow(1, PHONE, null),
            FakeContactsProvider.DataRow(2, PHONE, ""),
            FakeContactsProvider.DataRow(3, PHONE, "  "),
            FakeContactsProvider.DataRow(4, PHONE, "+15550104"),
        )
        val rows = reader.rows("alex")
        assertEquals(10, rows.map { it.contactId }.toSet().size)
        assertTrue(rows.none { it.mimetype == PHONE && it.data.isBlank() })
        assertEquals(listOf("+15550104"), rows.filter { it.mimetype == PHONE }.map { it.data })
    }

    @Test
    fun reader_escapes_like_wildcards_in_the_query() {
        provider.people = listOf(person(1, "Alex Example"), person(2, "100% Pizza"), person(3, "A_B"))
        assertEquals(listOf(2L), reader.rows("%").map { it.contactId })
        assertEquals("%\\%%", provider.contactsArgs.first())
        assertTrue(provider.contactsSelection!!.contains("ESCAPE"))
        assertEquals(listOf(3L), reader.rows("_").map { it.contactId })
    }

    @Test
    fun reader_tags_a_connected_app_row_with_its_app_and_its_own_text() {
        apps[SIGNAL_MSG] = "Signal"
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(FakeContactsProvider.DataRow(1, SIGNAL_MSG, "+15550100", rowId = 5, customLabel = "Message +15550100"))
        val row = reader.rows("alex").single()
        assertEquals("Signal", row.appName)
        assertEquals("pkg.signal", row.appPackage)
        assertEquals("Message +15550100", row.text)
        assertEquals(5L, row.rowId)
    }

    @Test
    fun reader_falls_back_to_data4_for_an_app_that_words_its_row_there_like_meet() {
        apps[SIGNAL_MSG] = "Meet"
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(FakeContactsProvider.DataRow(1, SIGNAL_MSG, "a@example.com", rowId = 5, data4 = "Video call on Meet"))
        assertEquals("Video call on Meet", reader.rows("alex").single().text)
    }

    @Test
    fun reader_drops_a_custom_row_that_no_installed_app_can_open() {
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(FakeContactsProvider.DataRow(1, SIGNAL_MSG, "+15550100"))
        assertEquals(listOf(NAME_ITEM), reader.rows("alex").map { it.mimetype })
    }

    @Test
    fun reader_ignores_standard_kinds_other_than_phone_and_email_and_keeps_those_untagged() {
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(
            FakeContactsProvider.DataRow(1, "vnd.android.cursor.item/postal-address_v2", "1 Main St"),
            FakeContactsProvider.DataRow(1, PHONE, "+15550100", type = 2),
        )
        val rows = reader.rows("alex")
        assertEquals(listOf(PHONE), rows.map { it.mimetype })
        assertEquals(null, rows.single().appName)
        assertEquals(null, rows.single().text)
    }

    @Test
    fun reader_reads_a_connected_app_whose_mimetype_has_no_vnd_part_like_meet() {
        val meet = "vnd.android.cursor.item/com.google.android.apps.tachyon.phone"
        apps[meet] = "Meet"
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(
            FakeContactsProvider.DataRow(1, meet, "+15550100", rowId = 6, customLabel = "Video call +15550100"),
            FakeContactsProvider.DataRow(1, "vnd.android.cursor.item/photo", "x"),
            FakeContactsProvider.DataRow(1, "vnd.android.cursor.item/name", "Alex Example"),
        )
        val rows = reader.rows("alex")
        assertEquals(listOf(meet), rows.map { it.mimetype })
        assertEquals("Meet", rows.single().appName)
        // Standard kinds are never offered to the package manager.
        assertEquals(listOf(meet), resolved)
    }

    @Test
    fun reader_returns_a_phone_and_three_connected_rows_from_one_data_query() {
        apps[SIGNAL_MSG] = "Signal"; apps[SIGNAL_CALL] = "Signal"; apps[SIGNAL_VIDEO] = "Signal"
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(
            FakeContactsProvider.DataRow(1, PHONE, "+15550100", type = 2),
            FakeContactsProvider.DataRow(1, SIGNAL_MSG, "+15550100", rowId = 1),
            FakeContactsProvider.DataRow(1, SIGNAL_CALL, "+15550100", rowId = 2),
            FakeContactsProvider.DataRow(1, SIGNAL_VIDEO, "+15550100", rowId = 3),
        )
        assertEquals(4, reader.rows("alex").size)
        assertEquals(1, provider.dataQueries)
    }

    @Test
    fun reader_asks_for_an_apps_name_once_per_mimetype_per_read() {
        apps[SIGNAL_MSG] = "Signal"
        provider.people = listOf(person(1, "Alex Example"), person(2, "Alexa Other"))
        provider.data = listOf(
            FakeContactsProvider.DataRow(1, SIGNAL_MSG, "+15550100", rowId = 1),
            FakeContactsProvider.DataRow(2, SIGNAL_MSG, "+15550101", rowId = 2),
        )
        assertEquals(2, reader.rows("alex").size)
        assertEquals(listOf(SIGNAL_MSG), resolved)
        reader.rows("alex")
        assertEquals(listOf(SIGNAL_MSG, SIGNAL_MSG), resolved)
    }

    @Test
    fun reader_survives_a_package_manager_that_throws() {
        resolverThrows = true
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(FakeContactsProvider.DataRow(1, SIGNAL_MSG, "+15550100"))
        assertEquals(listOf(NAME_ITEM), reader.rows("alex").map { it.mimetype })
    }

    @Test
    fun reader_returns_empty_when_permission_is_missing() {
        provider.people = listOf(person(1, "Alex Example"))
        provider.denied = true
        assertTrue(reader.rows("alex").isEmpty())
    }

    @Test
    fun reader_carries_the_data_row_id_so_the_row_can_be_launched() {
        provider.people = listOf(person(1, "Alex Example"))
        apps["vnd.android.cursor.item/vnd.com.whatsapp.profile"] = "WhatsApp"
        provider.data = listOf(FakeContactsProvider.DataRow(1, "vnd.android.cursor.item/vnd.com.whatsapp.profile", "15550100@s.whatsapp.net", rowId = 77))
        assertEquals(listOf(77L), reader.rows("alex").map { it.rowId })
    }

    @Test
    fun reader_carries_the_kind_of_a_number_and_a_custom_name() {
        provider.people = listOf(person(1, "Alex Example"))
        provider.data = listOf(
            FakeContactsProvider.DataRow(1, PHONE, "+15550100", rowId = 11, type = 2),
            FakeContactsProvider.DataRow(1, PHONE, "+15550199", rowId = 12, type = 0, customLabel = "Boat"),
            FakeContactsProvider.DataRow(1, PHONE, "+15550150", rowId = 13),
        )
        val rows = reader.rows("alex").filter { it.mimetype == PHONE }.associateBy { it.rowId }
        assertEquals("Mobile", rows.getValue(11).typeLabel)
        assertEquals("Boat", rows.getValue(12).typeLabel)
        assertEquals(null, rows.getValue(13).typeLabel)
    }

    @Test
    fun reader_reads_given_contacts_in_one_query_each_with_a_marker_for_one_without_rows() {
        provider.people = listOf(person(1, "Alex Example"), person(2, "Matt Lastname"), person(3, "Other"))
        provider.data = listOf(
            FakeContactsProvider.DataRow(1, PHONE, "+15550101"),
            FakeContactsProvider.DataRow(1, PHONE, "+15550102"),
            FakeContactsProvider.DataRow(3, PHONE, "+15550103"),
        )
        val rows = reader.rowsForContacts(setOf(1L, 2L, 4L))
        assertEquals(1, provider.contactsQueries)
        assertEquals(1, provider.dataQueries)
        assertEquals(setOf(1L, 2L), provider.dataRequestedIds)
        assertEquals(listOf("+15550101", "+15550102"), rows.filter { it.contactId == 1L }.map { it.data })
        assertEquals(NAME_ITEM, rows.single { it.contactId == 2L }.mimetype)
        // A gone contact and one not asked for give no rows.
        assertTrue(rows.none { it.contactId == 3L || it.contactId == 4L })
    }

    @Test
    fun reader_for_contacts_is_empty_when_permission_is_missing_or_none_asked() {
        provider.people = listOf(person(1, "Alex Example"))
        assertTrue(reader.rowsForContacts(emptySet()).isEmpty())
        assertEquals(0, provider.contactsQueries)
        provider.denied = true
        assertTrue(reader.rowsForContacts(setOf(1L)).isEmpty())
    }
}
