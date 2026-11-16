package me.jhot.swiftcurrent.shortcuts

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.LauncherUpdate
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Settings
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.search.LaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class RecordingStarter(var failWith: Exception? = null) : IntentStarter {
    val started = mutableListOf<Intent>()
    override fun start(intent: Intent) {
        failWith?.let { throw it }
        started += intent
    }
}

private class RecordingTaker(var failWith: Exception? = null) : UriPermissionTaker {
    val taken = mutableListOf<Pair<Uri, Int>>()
    override fun takePersistable(uri: Uri, modeFlags: Int) {
        failWith?.let { throw it }
        taken += uri to modeFlags
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UserShortcutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val creator = Creator("pkg.alpha.maps/pkg.alpha.maps.Friend", "Friend's location", "Alpha Maps")
    private val messages = LaunchMessages("gone", "not allowed", "unusable")
    private val store = MemoryLauncherStore()
    private val starter = RecordingStarter()
    private val taker = RecordingTaker()

    private fun shortcuts(ids: Iterator<String> = listOf("id-1", "id-2").iterator()) =
        UserShortcuts(starter, messages, taker, BitmapShortcutIcons(context)) { ids.next() }

    /** Writes through the memory store, as the shell does through its own update. */
    private suspend fun UserShortcuts.add(creator: Creator, result: Intent) = addFromResult(creator, result, store::update)

    private fun result(
        name: String? = "Friend Bob",
        intent: Intent? = Intent(Intent.ACTION_VIEW, Uri.parse("geo:1,2")).setPackage("pkg.alpha.maps"),
        icon: Bitmap? = null,
    ) = Intent().apply {
        if (intent != null) putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
        if (name != null) putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
        if (icon != null) putExtra(Intent.EXTRA_SHORTCUT_ICON, icon)
    }

    private fun stored(i: Int = 0) = store.data.userShortcuts[i]

    @Test
    fun result_intent_is_stored_with_label_and_icon() = runBlocking {
        val big = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2266AA.toInt()) }
        val out = shortcuts().add(creator, result(icon = big))
        assertEquals(AddOutcome.Added("id-1"), out)
        val s = stored()
        assertEquals("id-1", s.id)
        assertEquals("pkg.alpha.maps", s.appPackage)
        assertEquals("Friend Bob", s.label)
        val back = Intent.parseUri(s.intentUri, Intent.URI_INTENT_SCHEME)
        assertEquals(Intent.ACTION_VIEW, back.action)
        assertEquals("geo:1,2", back.dataString)
        // PNG, downscaled with the aspect ratio kept.
        val decoded = BitmapFactory.decodeByteArray(s.iconBytes, 0, s.iconBytes!!.size)
        assertNotNull(decoded)
        assertTrue("${decoded.width}x${decoded.height}", decoded.width == 96 && decoded.height == 64)
    }

    @Test
    fun a_write_that_did_not_land_is_an_error_not_an_added_shortcut() = runBlocking {
        // The shell's writer logs a failed write and hands back the data it already had.
        val unchanged: LauncherUpdate = { store.data }
        val failure = runCatching { shortcuts().addFromResult(creator, result(), unchanged) }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
    }

    @Test
    fun the_same_shortcut_made_twice_is_stored_once_and_the_second_is_a_duplicate() = runBlocking {
        val s = shortcuts()
        assertEquals(AddOutcome.Added("id-1"), s.add(creator, result()))
        assertEquals(AddOutcome.Duplicate, s.add(creator, result()))
        assertEquals(1, store.data.userShortcuts.size)
    }

    @Test
    fun a_different_label_or_target_is_not_a_duplicate() = runBlocking {
        val s = shortcuts(listOf("id-1", "id-2", "id-3").iterator())
        assertEquals(AddOutcome.Added("id-1"), s.add(creator, result()))
        assertEquals(AddOutcome.Added("id-2"), s.add(creator, result(name = "Friend Carol")))
        val other = Intent(Intent.ACTION_VIEW, Uri.parse("geo:9,9")).setPackage("pkg.alpha.maps")
        assertEquals(AddOutcome.Added("id-3"), s.add(creator, result(intent = other)))
        assertEquals(3, store.data.userShortcuts.size)
    }

    @Test
    fun a_result_without_an_icon_still_stores_the_shortcut() = runBlocking {
        assertTrue(shortcuts().add(creator, result()) is AddOutcome.Added)
        assertEquals(null, stored().iconBytes)
    }

    @Test
    fun a_result_without_the_intent_or_the_name_is_rejected_and_nothing_is_persisted() = runBlocking {
        val s = shortcuts()
        assertEquals(AddOutcome.Rejected(RejectReason.MissingIntent), s.add(creator, result(intent = null)))
        assertEquals(AddOutcome.Rejected(RejectReason.MissingName), s.add(creator, result(name = null)))
        assertEquals(AddOutcome.Rejected(RejectReason.MissingName), s.add(creator, result(name = "   ")))
        assertEquals(AddOutcome.Rejected(RejectReason.MissingIntent), s.add(creator, Intent()))
        assertEquals(0, store.writes)
        assertTrue(taker.taken.isEmpty())
    }

    @Test
    fun a_result_that_targets_another_apps_component_is_rejected_when_added() = runBlocking {
        val other = Intent().setClassName("pkg.evil", "pkg.evil.Main")
        assertEquals(AddOutcome.Rejected(RejectReason.WrongTarget), shortcuts().add(creator, result(intent = other)))
        assertEquals(0, store.writes)
    }

    @Test
    fun content_uri_result_takes_persistable_permission_when_offered() = runBlocking {
        val uri = Uri.parse("content://pkg.alpha.maps.files/contact/7")
        val offered = result().apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        assertTrue(shortcuts().add(creator, offered) is AddOutcome.Added)
        assertEquals(listOf(uri to Intent.FLAG_GRANT_READ_URI_PERMISSION), taker.taken)
    }

    @Test
    fun clip_data_uris_are_covered_and_a_grant_that_was_not_offered_is_left_alone() = runBlocking {
        val uri = Uri.parse("content://pkg.alpha.maps.files/photo/1")
        val clipped = result().apply {
            clipData = ClipData.newRawUri("p", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        shortcuts().add(creator, clipped)
        assertEquals(listOf(uri to Intent.FLAG_GRANT_READ_URI_PERMISSION), taker.taken)

        taker.taken.clear()
        val notOffered = result().apply { data = uri; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        shortcuts(listOf("x").iterator()).add(creator, notOffered)
        assertTrue(taker.taken.isEmpty())
    }

    @Test
    fun a_failing_permission_grant_never_crashes_or_loses_the_shortcut() = runBlocking {
        taker.failWith = SecurityException("no persistable grant")
        val offered = result().apply {
            data = Uri.parse("content://pkg.alpha.maps.files/contact/7")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        assertTrue(shortcuts().add(creator, offered) is AddOutcome.Added)
        assertEquals(1, store.data.userShortcuts.size)
    }

    @Test
    fun deleted_shortcut_is_removed_from_the_store() = runBlocking {
        val s = shortcuts()
        s.add(creator, result(name = "One"))
        s.add(creator, result(name = "Two"))
        s.delete("id-1", store::update)
        assertEquals(listOf("Two"), store.data.userShortcuts.map { it.label })
    }

    @Test
    fun deleting_a_user_shortcut_removes_it_from_favorites_folders_and_popups_and_disabled_keys() = runBlocking {
        val gone = Target.Shortcut("user/u1")
        val keep = Target.Shortcut("user/u2")
        fun item(id: String, t: Target) = PopupRow.Item(Entry(id, t))
        val nestedFolder = Entry(
            "nested", Target.Folder,
            popup = Popup(listOf(item("n-gone", gone), item("n-keep", keep), PopupRow.OwnShortcuts("pkg.a"))),
        )
        val folder = Entry("f", Target.Folder, popup = Popup(listOf(item("f-gone", gone), PopupRow.Item(nestedFolder), item("f-app", Target.App("p/.M")))))
        val appWithPopup = Entry("app", Target.App("p/.M"), popup = Popup(listOf(PopupRow.OwnShortcuts("p"), item("a-gone", gone))))
        val start = LauncherData(
            favorites = listOf(Entry("fav-gone", gone), Entry("fav-keep", keep), appWithPopup, folder),
            folders = listOf(folder, Entry("lib", Target.Folder, popup = Popup(listOf(item("l-gone", gone), item("l-keep", keep))))),
            userShortcuts = listOf(UserShortcut("u1", "pkg.a", "One", "i"), UserShortcut("u2", "pkg.a", "Two", "i")),
            settings = Settings(disabledShortcuts = setOf("user/u1", "user/u2", "pkg/x")),
        )
        val s = MemoryLauncherStore(start)
        UserShortcuts(starter, messages).delete("u1", s::update)
        val d = s.data
        assertEquals(listOf("u2"), d.userShortcuts.map { it.id })
        assertEquals(setOf("user/u2", "pkg/x"), d.settings.disabledShortcuts)
        assertEquals(listOf("fav-keep", "app", "f"), d.favorites.map { it.id })
        assertEquals(listOf(PopupRow.OwnShortcuts("p")), d.favorites.single { it.id == "app" }.popup!!.rows)
        val f = d.favorites.single { it.id == "f" }.popup!!.rows
        assertEquals(listOf("nested", "f-app"), f.map { (it as PopupRow.Item).entry.id })
        val nested = ((f.first() as PopupRow.Item).entry.popup!!.rows)
        assertEquals(listOf("n-keep"), nested.filterIsInstance<PopupRow.Item>().map { it.entry.id })
        assertTrue(nested.any { it is PopupRow.OwnShortcuts })
        assertEquals(listOf("f", "lib"), d.folders.map { it.id })
        assertEquals(listOf("l-keep"), d.folders.single { it.id == "lib" }.popup!!.rows.map { (it as PopupRow.Item).entry.id })
    }

    private fun userShortcut(uri: String, pkg: String = "pkg.alpha.maps") = UserShortcut("u", pkg, "L", uri)

    @Test
    fun launch_starts_the_stored_intent_in_a_new_task() {
        val uri = Intent(Intent.ACTION_VIEW, Uri.parse("geo:1,2")).toUri(Intent.URI_INTENT_SCHEME)
        assertEquals(LaunchOutcome.Launched, shortcuts().launch(userShortcut(uri)))
        val i = starter.started.single()
        assertEquals("geo:1,2", i.dataString)
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun launch_strips_uri_grant_flags_and_a_selector() {
        val raw = Intent(Intent.ACTION_VIEW, Uri.parse("content://pkg.alpha.maps.files/x"))
            .setPackage("pkg.alpha.maps")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // Hand-written like a tampered backup: the unsafe flag is spelled into the string itself.
        val uri = raw.toUri(Intent.URI_INTENT_SCHEME or Intent.URI_ALLOW_UNSAFE)
        assertEquals(LaunchOutcome.Launched, shortcuts().launch(userShortcut(uri)))
        val i = starter.started.single()
        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        assertEquals(0, i.flags and grants)
        assertEquals(null, i.selector)
    }

    @Test
    fun a_stored_intent_that_targets_another_package_is_rejected() {
        val explicit = Intent().setClassName("pkg.evil", "pkg.evil.Main").toUri(Intent.URI_INTENT_SCHEME)
        val out = shortcuts().launch(userShortcut(explicit))
        assertEquals(LaunchOutcome.Failed("not allowed"), out)
        val pinned = Intent(Intent.ACTION_VIEW, Uri.parse("geo:1,2")).setPackage("pkg.evil").toUri(Intent.URI_INTENT_SCHEME)
        assertEquals(LaunchOutcome.Failed("not allowed"), shortcuts().launch(userShortcut(pinned)))
        assertTrue(starter.started.isEmpty())
    }

    @Test
    fun launch_failures_become_readable_outcomes_and_surprises_are_not_swallowed() {
        val uri = Intent(Intent.ACTION_VIEW, Uri.parse("geo:1,2")).toUri(Intent.URI_INTENT_SCHEME)
        starter.failWith = ActivityNotFoundException("none")
        assertEquals(LaunchOutcome.Failed("gone"), shortcuts().launch(userShortcut(uri)))
        starter.failWith = SecurityException("denied")
        assertEquals(LaunchOutcome.Failed("not allowed"), shortcuts().launch(userShortcut(uri)))
        starter.failWith = null
        assertEquals(LaunchOutcome.Failed("unusable"), shortcuts().launch(userShortcut("intent:#Intent;action=x")))
        starter.failWith = IllegalStateException("bug")
        try {
            shortcuts().launch(userShortcut(uri))
            org.junit.Assert.fail("expected the surprise to propagate")
        } catch (e: IllegalStateException) {
            assertFalse(e.message.isNullOrEmpty())
        }
    }
}
