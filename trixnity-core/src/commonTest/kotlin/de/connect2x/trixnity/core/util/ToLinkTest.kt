package de.connect2x.trixnity.core.util

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import io.ktor.http.parametersOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ToLinkTest {
    @Test
    fun `escapes reserved characters in identifiers`() {
        val reference = Reference.User(UserId("@alice/#?%:example.org"))
        assertEquals("matrix:u/alice%2F%23%3F%25:example.org", reference.toLink())
        assertEquals(reference, Reference.parseLink(reference.toLink()))
    }

    @Test
    fun `serializes references with parameters`() {
        val parameters = parametersOf("via" to listOf("example.org", "elsewhere.org"), "action" to listOf("join"))
        val references =
            listOf(
                Reference.User(UserId("@alice:example.org")) to "matrix:u/alice:example.org",
                Reference.Room(RoomId("!room:example.org"), parameters) to
                    "matrix:roomid/room:example.org?via=example.org&via=elsewhere.org&action=join",
                Reference.RoomAlias(RoomAliasId("#room:example.org")) to "matrix:r/room:example.org",
                Reference.Event(RoomId("!room:example.org"), EventId("\$event")) to
                    "matrix:roomid/room:example.org/e/event",
            )
        references.forEach { (reference, link) ->
            assertEquals(link, reference.toLink())
            assertEquals(reference, Reference.parseLink(link))
        }
        assertEquals("matrix:e/event", Reference.Event(eventId = EventId("\$event")).toLink())
        assertEquals("https://example.org", Reference.Link("https://example.org").toLink())
        assertEquals("matrix:r/%23room:example.org", Reference.RoomAlias(RoomAliasId("##room:example.org")).toLink())
        assertEquals(
            "matrix:u/alice:example.org?label=a+b%26c",
            Reference.User(UserId("@alice:example.org"), parametersOf("label", "a b&c")).toLink(),
        )
    }
}
