package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.admin.GetLock
import de.connect2x.trixnity.clientserverapi.model.admin.GetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.SetLock
import de.connect2x.trixnity.clientserverapi.model.admin.SetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.WhoIs
import de.connect2x.trixnity.core.model.UserId
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

class AdminRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = EventContentSerializerMappings.default

    val handlerMock = mock<AdminApiHandler>()

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
                adminApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetSuspend() = testApplication {
        initCut()

        everySuspend { handlerMock.getSuspend(any()) }
            .returns(GetSuspend.Response(suspended = true))

        val response = client.get("/_matrix/client/v1/admin/suspend/@user:matrix.host") {
            bearerAuth("token")
        }

        assertSoftly(response) {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            body<String>() shouldBe """
                {"suspended":true}
            """.trimToFlatJson()
        }

        verifySuspend {
            handlerMock.getSuspend(assert {
                it.endpoint.userId shouldBe UserId("@user:matrix.host")
            })
        }
    }

    @Test
    fun shouldSetSuspend() = testApplication {
        initCut()

        everySuspend { handlerMock.setSuspend(any()) }
            .returns(SetSuspend.Response(suspended = true))

        val response = client.put("/_matrix/client/v1/admin/suspend/@user:matrix.host") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "suspended": true
                }
                """.trimIndent()
            )
        }

        assertSoftly(response) {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            body<String>() shouldBe """
                {"suspended":true}
            """.trimToFlatJson()
        }

        verifySuspend {
            handlerMock.setSuspend(assert {
                it.endpoint.userId shouldBe UserId("@user:matrix.host")
                it.requestBody shouldBe SetSuspend.Request(suspended = true)
            })
        }
    }

    @Test
    fun shouldGetLock() = testApplication {
        initCut()

        everySuspend { handlerMock.getLock(any()) }
            .returns(GetLock.Response(locked = true))

        val response = client.get("/_matrix/client/v1/admin/lock/@user:matrix.host") {
            bearerAuth("token")
        }

        assertSoftly(response) {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            body<String>() shouldBe """
                {"locked":true}
            """.trimToFlatJson()
        }

        verifySuspend {
            handlerMock.getLock(assert {
                it.endpoint.userId shouldBe UserId("@user:matrix.host")
            })
        }
    }

    @Test
    fun shouldSetLock() = testApplication {
        initCut()

        everySuspend { handlerMock.setLock(any()) }
            .returns(SetLock.Response(locked = true))

        val response = client.put("/_matrix/client/v1/admin/lock/@user:matrix.host") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "locked": true
                }
                """.trimIndent()
            )
        }

        assertSoftly(response) {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            body<String>() shouldBe """
                {"locked":true}
            """.trimToFlatJson()
        }

        verifySuspend {
            handlerMock.setLock(assert {
                it.endpoint.userId shouldBe UserId("@user:matrix.host")
                it.requestBody shouldBe SetLock.Request(locked = true)
            })
        }
    }

    @Test
    fun shouldWhoIs() = testApplication {
        initCut()

        everySuspend { handlerMock.whoIs(any()) }
            .returns(
                WhoIs.Response(
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
            )

        val response = client.get("/_matrix/client/v3/admin/whois/@peter:rabbit.rocks") {
            bearerAuth("token")
        }

        assertSoftly(response) {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            body<String>() shouldBe """
                {
                  "user_id": "@peter:rabbit.rocks",
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
                  }
                }
            """.trimToFlatJson()
        }

        verifySuspend {
            handlerMock.whoIs(assert {
                it.endpoint.userId shouldBe UserId("@peter:rabbit.rocks")
            })
        }
    }
}
