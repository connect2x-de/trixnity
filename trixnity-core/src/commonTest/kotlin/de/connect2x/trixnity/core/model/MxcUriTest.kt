package de.connect2x.trixnity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class MxcUriTest {
    @Test
    fun `construct from components`() {
        val uri = MxcUri(serverName = "[::1]:8448", mediaId = "Abc_123-")
        assertEquals("mxc://[::1]:8448/Abc_123-", uri.full)
        assertEquals("[::1]:8448", uri.serverName)
        assertEquals("Abc_123-", uri.mediaId)
        assertTrue(uri.isValid)
    }

    @Test
    fun `preserve case when reading components`() {
        val uri = MxcUri("MXC://MATRIX.ORG:8448/AbC")
        assertEquals("MATRIX.ORG:8448", uri.serverName)
        assertEquals("AbC", uri.mediaId)
        assertEquals("MXC://MATRIX.ORG:8448/AbC", uri.toString())
        assertTrue(uri.isValid)
    }

    @Test
    fun `validation is opt in`() {
        val uri = MxcUri("mxc://example.org/../file")
        assertEquals("mxc://example.org/../file", uri.full)
        assertFalse(uri.isValid)
        assertFalse(MxcUri.isValid(uri.full))
        assertTrue(MxcUri.isValid("mxc://example.org/abc"))
    }

    @Test
    fun `serialize as a string`() {
        assertEquals("\"mxc://example.org/abc\"", Json.encodeToString(MxcUri("mxc://example.org/abc")))
    }

    @Test
    fun `deserialize without implicit validation`() {
        assertEquals(MxcUri("mxc://example.org/abc"), Json.decodeFromString<MxcUri>("\"mxc://example.org/abc\""))
        assertEquals(MxcUri("invalid"), Json.decodeFromString<MxcUri>("\"invalid\""))
    }

    @Test
    fun `accept valid MXC URIs`() {
        listOf(
                "mxc://matrix.org/AQwafuaFswefuhsfAFAgsw",
                "mxc://matrix.org:8448/abc",
                "MXC://example.org/abc",
                "mXc://example.org/abc",
                "mxc://MATRIX.ORG/abc",
                "mxc://localhost/a",
                "mxc://127.0.0.1/abc",
                "mxc://127.0.0.1:8448/abc",
                "mxc://[1234:5678::abcd]/abc",
                "mxc://[::1]:8448/abc",
                "mxc://[::ffff:192.0.2.1]/abc",
                "mxc://example.org/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-",
            )
            .forEach { assertTrue(MxcUri.isValid(it), it) }
    }

    @Test
    fun `accept server names that require backtracking`() {
        listOf("1.2.3.4.example.org", "1.2.3.4567", "1.2.3.4.example.org:8448").forEach { server ->
            val uri = "mxc://$server/Abc_123-"
            assertTrue(MxcUri.isValid(uri), uri)
            assertTrue(MxcUri(uri).isValid, uri)
            assertEquals(server, MxcUri(uri).serverName)
            assertEquals("Abc_123-", MxcUri(uri).mediaId)
        }
    }

    @Test
    fun `reject trailing garbage and line terminators`() {
        listOf("/garbage", "?query", "#fragment", " ", "\n", "\r", "\r\n", "\u2028", "\u2029").forEach { suffix ->
            val uri = "mxc://example.org/abc$suffix"
            assertFalse(MxcUri.isValid(uri), uri)
            assertFalse(MxcUri(uri).isValid, uri)
        }
    }

    @Test
    fun `reject missing components and incorrect schemes`() {
        listOf(
                "",
                "mxc://",
                "mxc://example.org",
                "mxc://example.org/",
                "mxc:///abc",
                "example.org/abc",
                "https://example.org/abc",
                "mxc:/example.org/abc",
                " mxc://example.org/abc",
                "mxc://example.org/abc\n",
            )
            .forEach { assertFalse(MxcUri.isValid(it), it) }
    }

    @Test
    fun `reject invalid server names`() {
        listOf(
                "user@example.org",
                "user:password@example.org",
                "example.org:",
                "example.org:port",
                "example.org:123456",
                "example.org:-1",
                "example_org",
                "example org",
                "exämple.org",
                "%65xample.org",
                "::1",
                "[::1",
                "[gg::1]",
                "a".repeat(256),
            )
            .forEach { assertFalse(MxcUri.isValid("mxc://$it/abc"), it) }
    }

    @Test
    fun `accept grammar boundaries without imposing identifier length limits`() {
        val host = List(4) { "a".repeat(63) }.joinToString(".")
        assertTrue(MxcUri.isValid("mxc://$host:99999/" + "a".repeat(1024)))
        assertTrue(MxcUri.isValid("mxc://example.org:0/abc"))
        assertTrue(MxcUri.isValid("mxc://example.org:00001/abc"))
        assertTrue(MxcUri.isValid("mxc://example.org./abc"))
    }

    @Test
    fun `reject unsafe media IDs and extra URI components`() {
        listOf(
                ".",
                "..",
                "../../../etc/passwd",
                "%2e%2e%2fetc%2fpasswd",
                "%252e%252e",
                "%61bc",
                "abc/def",
                "abc/",
                "abc\\def",
                "abc?download=true",
                "abc#fragment",
                "abc=",
                "abc+",
                "abc.txt",
                "abc def",
                "äbc",
                "abc\u0000",
                "abc\r\n",
            )
            .forEach { assertFalse(MxcUri.isValid("mxc://example.org/$it"), it) }
    }
}
