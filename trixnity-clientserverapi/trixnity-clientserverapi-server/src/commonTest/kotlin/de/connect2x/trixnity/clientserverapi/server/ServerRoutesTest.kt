package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.server.Capabilities
import de.connect2x.trixnity.clientserverapi.model.server.Capability
import de.connect2x.trixnity.clientserverapi.model.server.GetCapabilities
import de.connect2x.trixnity.clientserverapi.model.server.GetVersions
import de.connect2x.trixnity.clientserverapi.model.server.Search
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
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
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlin.test.BeforeTest
import kotlin.test.Test

class ServerRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = EventContentSerializerMappings.default

    val handlerMock = mock<ServerApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
                }
            }
            matrixApiServer(json) {
                serverApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetVersions() = testApplication {
        initCut()
        everySuspend { handlerMock.getVersions(any()) }
            .returns(
                GetVersions.Response(
                    versions = emptyList(),
                    unstableFeatures = mapOf()
                )
            )
        val response = client.get("/_matrix/client/versions") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """
                {"versions":[],"unstable_features":{}}
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getVersions(any())
        }
    }

    @Test
    fun shouldGetCapabilities() = testApplication {
        initCut()
        everySuspend { handlerMock.getCapabilities(any()) }
            .returns(
                GetCapabilities.Response(
                    capabilities = Capabilities(
                        setOf(
                            Capability.ChangePassword(true),
                            Capability.RoomVersions("5", mapOf())
                        )
                    )
                )
            )
        val response = client.get("/_matrix/client/v3/capabilities") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """
                {
                  "capabilities": {
                    "m.change_password": {
                      "enabled": true
                    },
                    "m.room_versions": {
                      "default": "5",
                      "available": {}
                    }
                  }
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getCapabilities(any())
        }
    }

    @Test
    fun shouldSearch() = testApplication {
        initCut()
        everySuspend { handlerMock.search(any()) }
            .returns(
                Search.Response(
                    Search.Response.ResultCategories(
                        Search.Response.ResultCategories.RoomEventsResult(
                            count = 1224,
                            groups = mapOf(
                                "room_id" to mapOf(
                                    "!qPewotXpIctQySfjSy:localhost" to Search.Response.ResultCategories.RoomEventsResult.GroupValue(
                                        nextBatch = "BdgFsdfHSf-dsFD",
                                        order = 1,
                                        results = listOf("$144429830826TWwbB:localhost")
                                    )
                                )
                            ),
                            highlights = setOf("martians", "men"),
                            nextBatch = "5FdgFsd234dfgsdfFD",
                            results = listOf(
                                Search.Response.ResultCategories.RoomEventsResult.Results(
                                    rank = 0.00424866,
                                    result = MessageEvent(
                                        RoomMessageEventContent.TextBased.Text("This is an example text message"),
                                        id = EventId("$144429830826TWwbB:localhost"),
                                        originTimestamp = 1432735824653,
                                        roomId = RoomId("!qPewotXpIctQySfjSy:localhost"),
                                        sender = UserId("@example:example.org")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        val response = client.post("/_matrix/client/v3/search") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "search_categories": {
                    "room_events": {
                      "groupings": {
                        "group_by": [
                          {
                            "key": "room_id"
                          }
                        ]
                      },
                      "keys": [
                        "content.body"
                      ],
                      "order_by": "recent",
                      "search_term": "martians and men"
                    }
                  }
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """
                {
                  "search_categories": {
                    "room_events": {
                      "count": 1224,
                      "groups": {
                        "room_id": {
                          "!qPewotXpIctQySfjSy:localhost": {
                            "next_batch": "BdgFsdfHSf-dsFD",
                            "order": 1,
                            "results": [
                              "${'$'}144429830826TWwbB:localhost"
                            ]
                          }
                        }
                      },
                      "highlights": [
                        "martians",
                        "men"
                      ],
                      "next_batch": "5FdgFsd234dfgsdfFD",
                      "results": [
                        {
                          "rank": 0.00424866,
                          "result": {
                            "content": {
                              "body": "This is an example text message",
                              "msgtype": "m.text"
                            },
                            "event_id": "${'$'}144429830826TWwbB:localhost",
                            "origin_server_ts": 1432735824653,
                            "room_id": "!qPewotXpIctQySfjSy:localhost",
                            "sender": "@example:example.org",
                            "type": "m.room.message"
                          }
                        }
                      ]
                    }
                  }
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.search(assert {
                it.requestBody shouldBe Search.Request(
                    Search.Request.Categories(
                        Search.Request.Categories.RoomEventsCriteria(
                            groupings = Search.Request.Categories.RoomEventsCriteria.Groupings(
                                setOf(
                                    Search.Request.Categories.RoomEventsCriteria.Groupings.Groups(
                                        "room_id"
                                    )
                                )
                            ),
                            keys = setOf("content.body"),
                            orderBy = Search.Request.Categories.RoomEventsCriteria.Ordering.RECENT,
                            searchTerm = "martians and men"
                        )
                    )
                )
            })
        }
    }
}
