package me.jhot.swiftcurrent.shortcuts

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CopiedWebAddressTest {
    private val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)

    private fun copy(text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText("copied", text)
        if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        clipboard.setPrimaryClip(clip)
    }

    @Test
    fun a_link_with_a_web_scheme_is_offered_trimmed() {
        copy("  https://example.com/a?b=1 \n")
        assertEquals("https://example.com/a?b=1", copiedWebAddress(clipboard))
        copy("HTTP://Example.com")
        assertEquals("HTTP://Example.com", copiedWebAddress(clipboard))
    }

    @Test
    fun anything_without_an_explicit_web_scheme_is_not_offered() {
        listOf("hunter2", "123456", "example.com", "ftp://example.com", "javascript:alert(1)", "two words").forEach {
            copy(it)
            assertNull("'$it'", copiedWebAddress(clipboard))
        }
    }

    @Test
    fun a_clip_marked_sensitive_is_never_offered_even_when_it_looks_like_a_link() {
        copy("https://example.com/reset?token=abc", sensitive = true)
        assertNull(copiedWebAddress(clipboard))
    }

    @Test
    fun an_empty_clipboard_offers_nothing() {
        clipboard.clearPrimaryClip()
        assertNull(copiedWebAddress(clipboard))
    }
}
