package me.jhot.swiftcurrent.shortcuts

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WebLinksTest {
    private fun parsed(input: String) = WebLinks.parse(input)!!

    @Test
    fun a_bare_host_gets_https() {
        val link = parsed("example.com")
        assertEquals("https://example.com", link.uri)
        assertEquals("example.com", link.host)
    }

    @Test
    fun a_host_and_port_without_a_scheme_gets_https() {
        assertEquals("https://example.com:8443/x", parsed("example.com:8443/x").uri)
    }

    @Test
    fun scheme_and_host_are_lowercased_and_the_rest_is_kept() {
        assertEquals("http://example.com/Path?Q=1", parsed("  http://Example.COM/Path?Q=1 ").uri)
        assertEquals("https://example.com:8443/a", parsed("HTTPS://example.com:8443/a").uri)
    }

    @Test
    fun an_ipv6_literal_is_accepted() {
        assertEquals("[::1]", parsed("https://[::1]/x").host)
    }

    @Test
    fun a_unicode_host_is_accepted_as_typed_in_lowercase() {
        assertEquals("exämple.com", parsed("https://Exämple.com/").host)
    }

    @Test
    fun display_is_the_address_without_the_scheme() {
        assertEquals("example.com/a?b=1", parsed("https://example.com/a?b=1").display)
        assertEquals("example.com", parsed("https://example.com").display)
    }

    @Test
    fun the_default_name_is_the_host() {
        assertEquals("example.com", WebLinks.defaultName(parsed("https://example.com/a")))
    }

    @Test
    fun anything_that_is_not_an_http_address_is_rejected() {
        listOf(
            "", "   ", "javascript:alert(1)", "file:///etc/passwd", "content://x/y", "intent://x#Intent;end",
            "ftp://example.com", "https://", "http://", "https://user:pw@example.com", "https://exa mple.com",
        ).forEach { assertNull("should reject '$it'", WebLinks.parse(it)) }
    }

    @Test
    fun the_stored_intent_views_the_url_in_exactly_one_browser() {
        val intent = Intent.parseUri(WebLinks.intentUri(parsed("example.com"), "org.example.browser"), Intent.URI_INTENT_SCHEME)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com", intent.data.toString())
        assertEquals("org.example.browser", intent.`package`)
        assertNotNull(intent.categories)
        assertEquals(true, Intent.CATEGORY_BROWSABLE in intent.categories)
    }
}
