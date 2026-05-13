package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.admin.GetLock
import de.connect2x.trixnity.clientserverapi.model.admin.GetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.SetLock
import de.connect2x.trixnity.clientserverapi.model.admin.SetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.WhoIs
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.testutils.scopedMockEngine
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminApiClientTest : TrixnityBaseTest() {

    @Test
    fun shouldGetSuspend() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/admin/suspend/@user:matrix.host", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"suspended":true}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.admin.getSuspend(UserId("@user:matrix.host")).getOrThrow()
        result shouldBe GetSuspend.Response(suspended = true)
    }

    @Test
    fun shouldSetSuspend() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/admin/suspend/@user:matrix.host", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """{"suspended":true}"""
                    respond(
                        """{"suspended":true}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.admin.setSuspend(UserId("@user:matrix.host"), true).getOrThrow()
        result shouldBe SetSuspend.Response(suspended = true)
    }

    @Test
    fun shouldGetLock() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/admin/lock/@user:matrix.host", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"locked":true}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.admin.getLock(UserId("@user:matrix.host")).getOrThrow()
        result shouldBe GetLock.Response(locked = true)
    }

    @Test
    fun shouldSetLock() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/admin/lock/@user:matrix.host", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """{"locked":true}"""
                    respond(
                        """{"locked":true}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.admin.setLock(UserId("@user:matrix.host"), true).getOrThrow()
        result shouldBe SetLock.Response(locked = true)
    }

    @Test
    fun shouldWhoIs() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/admin/whois/@peter:rabbit.rocks", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                           {
                              "devices": {
                                "teapot": {
                                  "sessions": [
                                    {
                                      "connections": [
                                        {
                                          "ip": "127.0.0.1",
                                          "last_seen": 1411996332123,
                                          "user_agent": "curl/7.31.0-DEV"
                                        },
                                        {
                                          "ip": "10.0.0.2",
                                          "last_seen": 1411996332123,
                                          "user_agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36"
                                        }
                                      ]
                                    }
                                  ]
                                }
                              },
                              "user_id": "@peter:rabbit.rocks"
                            }
                       """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.admin.whoIs(UserId("@peter:rabbit.rocks")).getOrThrow() shouldBe WhoIs.Response(
            userId = UserId("@peter:rabbit.rocks"),
            devices = mapOf(
                "teapot" to WhoIs.Response.DeviceInfo(
                    setOf(
                        WhoIs.Response.DeviceInfo.SessionInfo(
                            setOf(
                                WhoIs.Response.DeviceInfo.SessionInfo.ConnectionInfo(
                                    ip = "127.0.0.1",
                                    lastSeen = 1411996332123,
                                    userAgent = "curl/7.31.0-DEV"
                                ),
                                WhoIs.Response.DeviceInfo.SessionInfo.ConnectionInfo(
                                    ip = "10.0.0.2",
                                    lastSeen = 1411996332123,
                                    userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36"
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
