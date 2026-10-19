package de.connect2x.trixnity.core.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsValidMxcUriTest {
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
            .forEach { assertTrue(isValidMxcUri(it), it) }
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
            .forEach { assertFalse(isValidMxcUri(it), it) }
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
            .forEach { assertFalse(isValidMxcUri("mxc://$it/abc"), it) }
    }

    @Test
    fun `accept grammar boundaries without imposing identifier length limits`() {
        val host = List(4) { "a".repeat(63) }.joinToString(".")
        assertTrue(isValidMxcUri("mxc://$host:99999/" + "a".repeat(1024)))
        assertTrue(isValidMxcUri("mxc://example.org:0/abc"))
        assertTrue(isValidMxcUri("mxc://example.org:00001/abc"))
        assertTrue(isValidMxcUri("mxc://example.org./abc"))
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
            .forEach { assertFalse(isValidMxcUri("mxc://example.org/$it"), it) }
    }
}
