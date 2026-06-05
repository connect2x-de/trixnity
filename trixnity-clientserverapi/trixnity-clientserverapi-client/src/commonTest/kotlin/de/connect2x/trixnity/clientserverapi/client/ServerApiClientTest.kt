package de.connect2x.trixnity.clientserverapi.client

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
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.testutils.scopedMockEngine
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerApiClientTest : TrixnityBaseTest() {
    @Test
    fun shouldGetVersions() = runTest {
        val response = GetVersions.Response(
            versions = emptyList(),
            unstableFeatures = mapOf()
        )
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/versions", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        Json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.server.getVersions().getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetCapabilities() = runTest {
        val response = GetCapabilities.Response(
            capabilities = Capabilities(
                setOf(
                    Capability.ChangePassword(true),
                    Capability.RoomVersions("5", mapOf())
                )
            )
        )
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/capabilities", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        Json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.server.getCapabilities().getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldSearch() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/search",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
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
                    """.trimToFlatJson()
                    respond(
                        """
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
                                          "$144429830826TWwbB:localhost"
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
                                        "event_id": "$144429830826TWwbB:localhost",
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.server.search(
            request = Search.Request(
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
        ).getOrThrow() shouldBe Search.Response(
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
    }
}
