package me.jhot.swiftcurrent.shortcuts

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.core.UserShortcut
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ChatLinkLabelTest {
    private fun shortcut(pkg: String, url: String) =
        UserShortcut("id", pkg, "Name", WebLinks.intentUri(WebLinks.parse(url)!!, pkg), null)

    @Test
    fun a_saved_chat_link_is_filed_under_google_chat_not_the_app_that_opens_it() {
        val chat = shortcut("com.google.android.gm", "https://chat.google.com/room/AAAA3Qj4PVo")
        assertEquals("Google Chat", userShortcutAppLabel(chat, "Gmail"))
    }

    @Test
    fun other_shortcuts_keep_their_apps_name() {
        assertEquals("Gmail", userShortcutAppLabel(shortcut("com.google.android.gm", "https://example.com/room/AAAA3Qj4PVo"), "Gmail"))
        assertEquals("Firefox", userShortcutAppLabel(shortcut("org.mozilla.firefox", "https://chat.google.com/room/AAAA3Qj4PVo"), "Firefox"))
    }
}
