package de.connect2x.trixnity.core.model

import de.connect2x.trixnity.core.util.Reference
import io.ktor.http.parametersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class MatrixUriTest {
    @Test
    fun `round trips user uris used in vCards and directory endpoints`() {
        listOf(UserId("@alice:example.org"), UserId("@alice/bob:example.org"), UserId("@alice:[::1]:8448")).forEach {
            userId ->
            val uri = MatrixUri.from(userId)
            assertEquals(Reference.User(userId), uri.toReference())
            assertEquals(uri.full, checkNotNull(uri.toReference()?.toMatrixUri()).full)
            assertTrue(uri.isValid, uri.full)
        }
    }

    @Test
    fun `round trips notification room uris`() {
        listOf(RoomId("!room:example.org"), RoomId("!opaqueRoomId")).forEach { roomId ->
            val callback = "messenger://account/${MatrixUri.from(roomId).full}"
            val uri = MatrixUri(callback.removePrefix("messenger://account/"))
            assertEquals(Reference.Room(roomId), uri.toReference())
            assertTrue(uri.isValid)
        }
    }

    @Test
    fun `round trips event ids and encoded repeated parameters`() {
        val parameters =
            parametersOf("via" to listOf("example.org", "elsewhere.org"), "label" to listOf("a b&c", "a+b%"))
        val reference = Reference.Event(RoomId("!room:example.org"), EventId("\$a/b+c:example.org"), parameters)
        val uri = checkNotNull(reference.toMatrixUri())
        assertEquals(reference, uri.toReference())
        assertTrue(uri.isValid)
    }

    @Test
    fun `preserves roomless event serialization without treating it as valid`() {
        val uri = checkNotNull(Reference.Event(eventId = EventId("\$event")).toMatrixUri())
        assertEquals("matrix:e/event", uri.full)
        assertFalse(uri.isValid)
        assertNull(uri.toReference())
    }

    @Test
    fun `extracts user ids ignoring authority query and fragment`() {
        listOf(
                "matrix:u/alice:example.org",
                "matrix:u/alice:example.org?action=chat#fragment",
                "matrix://ignored/u/alice:example.org?foo=bar#fragment",
                "matrix:u/alice%3Aexample.org",
            )
            .forEach {
                assertEquals(UserId("@alice:example.org"), (MatrixUri(it).toReference() as? Reference.User)?.userId)
            }
        assertEquals(
            Reference.User(UserId("@alice:example.org:8448")),
            MatrixUri("matrix:u/alice:example.org:8448").toReference(),
        )
    }

    @Test
    fun `rejects invalid user uris`() {
        listOf(
                "",
                "@alice:example.org",
                "https://matrix.to/#/@alice:example.org",
                "matrix:u/alice",
                "matrix:u/:example.org",
                "matrix:u/alice:",
                "matrix:u/alice%ZZ:example.org",
                "matrix:u/alice:example.org/extra",
                "matrix:u/alice:example.org/e/event",
                "matrix:u/${"a".repeat(256)}:example.org",
            )
            .forEach { assertFalse(MatrixUri(it).isValid, it) }
    }

    @Test
    fun `construct from identifiers`() {
        val parameters = parametersOf("via", "example.org")
        val userId = UserId("@alice:example.org")
        val roomId = RoomId("!room:example.org")
        val roomAliasId = RoomAliasId("#room:example.org")
        val eventId = EventId("\$event")
        listOf(
                MatrixUri.from(userId) to Reference.User(userId),
                MatrixUri.from(roomId, parameters) to Reference.Room(roomId, parameters),
                MatrixUri.from(roomAliasId) to Reference.RoomAlias(roomAliasId),
                MatrixUri.from(roomId, eventId, parameters) to Reference.Event(roomId, eventId, parameters),
            )
            .forEach { (uri, reference) ->
                assertEquals(reference, uri.toReference())
                assertEquals(uri, reference.toMatrixUri())
                assertTrue(uri.isValid, uri.full)
                assertTrue(MatrixUri.isValid(uri.full), uri.full)
            }
        assertEquals("matrix:u/alice:example.org", MatrixUri.from(userId).full)
    }

    @Test
    fun `preserve original uri and parse parameters`() {
        val full = "MATRIX://ignored/u/alice%3Aexample.org?action=chat#fragment"
        val uri = MatrixUri(full)
        assertEquals(full, uri.full)
        assertEquals(full, uri.toString())
        assertEquals(Reference.User(UserId("@alice:example.org"), parametersOf("action", "chat")), uri.toReference())
        assertTrue(uri.isValid)
    }

    @Test
    fun `ordinary links are not Matrix URIs`() {
        assertNull(Reference.Link("https://example.org").toMatrixUri())
    }

    @Test
    fun `validation is opt in`() {
        val uri = MatrixUri("matrix:u/alice")
        assertEquals("matrix:u/alice", uri.full)
        assertEquals(Reference.User(UserId("@alice")), uri.toReference())
        assertFalse(uri.isValid)
        listOf(
                "invalid",
                "https://matrix.to/#/@alice:example.org",
                "matrix:u/alice:example.org/extra",
                "matrix:u/alice:example.org/e/event",
                "matrix:u/alice%ZZ:example.org",
            )
            .forEach {
                assertNull(MatrixUri(it).toReference(), it)
                assertFalse(MatrixUri.isValid(it), it)
            }
    }

    @Test
    fun `serialize as a string without implicit validation`() {
        listOf("matrix:u/alice:example.org", "invalid").forEach {
            assertEquals(Json.encodeToString(it), Json.encodeToString(MatrixUri(it)))
            assertEquals(MatrixUri(it), Json.decodeFromString<MatrixUri>(Json.encodeToString(it)))
        }
    }
}
