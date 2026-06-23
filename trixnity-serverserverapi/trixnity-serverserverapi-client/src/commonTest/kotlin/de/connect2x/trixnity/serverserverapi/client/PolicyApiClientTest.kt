package de.connect2x.trixnity.serverserverapi.client

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.testutils.scopedMockEngine
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyApiClientTest : TrixnityBaseTest() {
    private val pdu: Signed<PersistentDataUnit<*>, String> = Signed(
        PersistentDataUnit.PersistentDataUnitV12.PersistentMessageDataUnitV12(
            authEvents = listOf(),
            content = RoomMessageEventContent.TextBased.Text("hi"),
            depth = 12u,
            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
            originTimestamp = 1404838188000,
            prevEvents = listOf(),
            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
            sender = UserId("@alice:example.com"),
            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
        ),
        mapOf(
            "matrix.org" to keysOf(
                Key.Ed25519Key(
                    "key",
                    "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                )
            )
        )
    )

    private val pduJson = """
        {
          "auth_events": [],
          "content": {
            "body": "hi",
            "msgtype": "m.text"
          },
          "depth": 12,
          "hashes": {
            "sha256": "thishashcoversallfieldsincasethisisredacted"
          },
          "origin_server_ts": 1404838188000,
          "prev_events": [],
          "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
          "sender": "@alice:example.com",
          "type": "m.room.message",
          "unsigned": {
            "age": 4612
          },
          "signatures": {
              "matrix.org": {
                "ed25519:key": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
              }
          }                      
        }
    """.trimToFlatJson()


    @Test
    fun shouldSign() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/policy/v1/sign", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        pduJson, request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                              "policy.example.org": {
                                "ed25519:policy_server": "zLFxllD0pbBuBpfHh8NuHNaICpReF/PAOpUQTsw+bFGKiGfDNAsnhcP7pbrmhhpfbOAxIdLraQLeeiXBryLmBw"
                              }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.policy.sign(
            pdu
        ).getOrThrow() shouldBe mapOf(
            "policy.example.org" to keysOf(
                Key.Ed25519Key(
                    "policy_server",
                    "zLFxllD0pbBuBpfHh8NuHNaICpReF/PAOpUQTsw+bFGKiGfDNAsnhcP7pbrmhhpfbOAxIdLraQLeeiXBryLmBw"
                )
            )
        )
    }
}
