package de.connect2x.trixnity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RoomIdTest {
    private fun assertValidation(id: String, expectedValid: Boolean, expectedReasonable: Boolean = expectedValid) {
        assertEquals(expected = expectedValid, actual = RoomId.isValid(id), message = "isValid($id)")
        assertEquals(expected = expectedValid, actual = RoomId(id).isValid, message = "RoomId($id).isValid")
        assertEquals(expected = expectedReasonable, actual = RoomId.isReasonable(id), message = "isReasonable($id)")
        assertEquals(
            expected = expectedReasonable,
            actual = RoomId(id).isReasonable,
            message = "RoomId($id).isReasonable",
        )
    }

    @Test
    fun `accept legacy room ids`() {
        assertValidation("!parent:example.org", expectedValid = true)
    }

    @Test
    fun `accept serverless room ids`() {
        for (id in listOf("!a", "!AZaz09-._~", "!8sQ1Ex3dBJbCgFm4eN0LkSUAtjbAEdZ8wVSA1dl1Ytk")) {
            assertValidation(id, expectedValid = true)
        }
    }

    @Test
    fun `accept server name variants`() {
        for (server in
            listOf(
                "example.org",
                "EXAMPLE.org",
                "localhost",
                "matrix-1.example.org",
                "127.0.0.1",
                "1.2.3.4.example.org",
                "[::1]",
                "[2001:db8::1]",
                "[::ffff:192.0.2.1]",
            )) {
            for (port in listOf("", ":1", ":8448", ":65535")) {
                assertValidation("!parent:$server$port", expectedValid = true)
            }
        }
    }

    @Test
    fun `accept broad legacy localparts`() {
        for (localpart in listOf("a b", "café", "a/b", "a+b", "a\nb")) {
            assertValidation("!$localpart:example.org", expectedValid = true)
            assertValidation("!$localpart", expectedValid = false)
        }
    }

    @Test
    fun `reject malformed room ids`() {
        for (id in
            listOf(
                "",
                "!",
                "parent:example.org",
                "@parent:example.org",
                "!:example.org",
                "!parent:",
                "!parent:example.org:",
                "!parent:example.org:abc",
                "!parent:example.org:123456",
                "!parent:example.org:80:90",
                "!parent:bad_name",
                "!parent:2001:db8::1",
                "!parent:[::1",
                "!parent:[]",
                "!parent:[gg::1]",
                "!parent/",
                "!parent=",
            )) {
            assertValidation(id, expectedValid = false)
        }
    }

    @Test
    fun `reject surrogate code units`() {
        // Construct at runtime because JS compilation can replace unpaired surrogates in string literals.
        for (codeUnit in listOf(0xD800, 0xDFFF)) {
            assertValidation("!a${codeUnit.toChar()}:example.org", expectedValid = false)
        }
    }

    @Test
    fun `reject trailing garbage and line terminators`() {
        for (id in listOf("!parent", "!parent:example.org", "!parent:127.0.0.1:8448", "!parent:[::1]")) {
            for (suffix in listOf("/garbage", "?query", "#fragment", " ", "\n", "\r", "\r\n", "\u2028", "\u2029")) {
                assertValidation(id + suffix, expectedValid = false)
            }
            assertValidation("prefix" + id, expectedValid = false)
        }
    }

    @Test
    fun `enforce length boundaries including sigil and server`() {
        for (length in listOf(254, 255, 256)) {
            assertValidation("!" + "a".repeat(length - 1), expectedValid = length <= 255)
            val suffix = ":example.org:8448"
            assertValidation("!" + "a".repeat(length - 1 - suffix.length) + suffix, expectedValid = length <= 255)
        }
    }
}
