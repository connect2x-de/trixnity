package de.connect2x.trixnity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatrixIdValidationTest {
    @Test
    fun `accept legacy and serverless event ids`() {
        for (id in listOf("\$event:example.org", "\$AZaz09-._~")) {
            assertTrue(EventId.isValid(id), id)
            assertTrue(EventId(id).isValid, id)
            assertTrue(EventId.isReasonable(id), id)
            assertTrue(EventId(id).isReasonable, id)
        }
        assertTrue(EventId.isValid("\$a b:example.org"))
        assertFalse(EventId.isReasonable("\$a b:example.org"))
    }

    @Test
    fun `backtrack server name alternatives in all validators`() {
        for (server in listOf("1.2.3.4.example.org", "1.2.3.4567", "127.0.0.1", "[::1]")) {
            for (port in listOf("", ":8448")) {
                val user = "@user:$server$port"
                val alias = "#alias:$server$port"
                val event = "\$event:$server$port"
                assertTrue(UserId.isValid(user), user)
                assertTrue(UserId(user).isValid, user)
                assertTrue(UserId.isReasonable(user), user)
                assertTrue(UserId(user).isReasonable, user)
                assertTrue(RoomAliasId.isValid(alias), alias)
                assertTrue(RoomAliasId(alias).isValid, alias)
                assertTrue(EventId.isValid(event), event)
                assertTrue(EventId(event).isValid, event)
                assertTrue(EventId.isReasonable(event), event)
                assertTrue(EventId(event).isReasonable, event)
            }
        }
    }

    @Test
    fun `reject trailing garbage in related validators`() {
        for (suffix in listOf("/garbage", "\n", "\r\n", "\u2028", "\u2029")) {
            assertFalse(UserId.isValid("@user:example.org$suffix"))
            assertFalse(UserId.isReasonable("@user:example.org$suffix"))
            assertFalse(RoomAliasId.isValid("#alias:example.org$suffix"))
            assertFalse(EventId.isValid("\$event:example.org$suffix"))
            assertFalse(EventId.isReasonable("\$event:example.org$suffix"))
        }
    }

    @Test
    fun `preserve event id length boundaries`() {
        for (length in listOf(254, 255, 256)) {
            for (suffix in listOf("", ":example.org")) {
                val id = "$" + "a".repeat(length - 1 - suffix.length) + suffix
                assertEquals(expected = length <= 255, actual = EventId.isValid(id))
                assertEquals(expected = length <= 255, actual = EventId.isReasonable(id))
            }
        }
    }
}
