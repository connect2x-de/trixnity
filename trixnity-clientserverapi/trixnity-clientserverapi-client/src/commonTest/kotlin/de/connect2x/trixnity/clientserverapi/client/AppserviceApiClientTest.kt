package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.appservice.Ping
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.testutils.scopedMockEngine
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AppserviceApiClientTest : TrixnityBaseTest() {

    @Test
    fun shouldPing() = runTest {
        val matrixRestClient =
            MatrixClientServerApiClientImpl(
                baseUrl = Url("https://matrix.host"),
                httpClientEngine =
                    scopedMockEngine {
                        addHandler { request ->
                            assertEquals("/_matrix/client/v1/appservice/appId/ping", request.url.fullPath)
                            assertEquals(HttpMethod.Post, request.method)
                            request.body.toByteArray().decodeToString() shouldBe """{"transaction_id":"1"}"""
                            respond(
                                """
                                {
                                  "duration_ms": 1234
                                }
                                """
                                    .trimIndent(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString()),
                            )
                        }
                    },
            )
        matrixRestClient.appservice.ping("appId", "1").getOrThrow() shouldBe Ping.Response(1234)
    }
}
