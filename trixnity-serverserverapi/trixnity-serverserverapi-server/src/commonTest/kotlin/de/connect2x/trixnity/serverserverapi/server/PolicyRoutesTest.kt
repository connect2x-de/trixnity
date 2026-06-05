package de.connect2x.trixnity.serverserverapi.server

import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.serverserverapi.model.SignedPersistentDataUnit
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.resetAnswers
import dev.mokkery.resetCalls
import dev.mokkery.verifySuspend
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlin.test.BeforeTest
import kotlin.test.Test

class PolicyRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventAndDataUnitJson(TestRoomVersionStore("12"))
    private val mapping = EventContentSerializerMappings.default

    val handlerMock = mock<PolicyApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixSignatureAuth(hostname = "") {
                authenticationFunction = { SignatureAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            install(ConvertMediaPlugin)
            matrixApiServer(json) {
                policyApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    private val pdu: SignedPersistentDataUnit<*> = Signed(
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
                Ed25519Key(
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
    fun shouldSendTransaction() = testApplication {
        initCut()
        everySuspend { handlerMock.sign(any()) }
            .returns(
                mapOf(
                    "policy.example.org" to keysOf(
                        Ed25519Key(
                            "policy_server",
                            "zLFxllD0pbBuBpfHh8NuHNaICpReF/PAOpUQTsw+bFGKiGfDNAsnhcP7pbrmhhpfbOAxIdLraQLeeiXBryLmBw"
                        )
                    )
                )
            )
        val response = client.post("/_matrix/policy/v1/sign") {
            contentType(ContentType.Application.Json)
            someSignature()
            setBody(pduJson)
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "policy.example.org": {
                        "ed25519:policy_server": "zLFxllD0pbBuBpfHh8NuHNaICpReF/PAOpUQTsw+bFGKiGfDNAsnhcP7pbrmhhpfbOAxIdLraQLeeiXBryLmBw"
                      }
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.sign(assert {
                it.requestBody shouldBe pdu
            })
        }
    }
}
