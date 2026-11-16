package me.jhot.swiftcurrent.search.sources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SearchStringsTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun the_plain_english_fake_matches_the_resources() {
        val real = ResourceSearchStrings(res)
        assertEquals(EnglishSearchStrings.hiddenBadge, real.hiddenBadge)
        assertEquals(EnglishSearchStrings.archivedBadge, real.archivedBadge)
        assertEquals(EnglishSearchStrings.call, real.call)
        assertEquals(EnglishSearchStrings.message, real.message)
        assertEquals(EnglishSearchStrings.email, real.email)
        assertEquals(EnglishSearchStrings.callTo("mobile"), real.callTo("mobile"))
        assertEquals(EnglishSearchStrings.messageTo("work"), real.messageTo("work"))
        assertEquals(EnglishSearchStrings.emailTo("a@b.c"), real.emailTo("a@b.c"))
    }

    @Test
    fun the_shortcut_launch_messages_exist_as_resources() {
        listOf(R.string.shortcut_launch_gone, R.string.shortcut_launch_unavailable, R.string.shortcut_launch_needs_default_home)
            .forEach { assertEquals(false, res.getString(it).isBlank()) }
    }
}
