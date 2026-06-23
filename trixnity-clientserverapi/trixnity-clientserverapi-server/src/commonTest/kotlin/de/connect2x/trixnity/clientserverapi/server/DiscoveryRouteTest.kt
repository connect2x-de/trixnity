package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.discovery.DiscoveryInformation
import de.connect2x.trixnity.clientserverapi.model.discovery.GetPolicyServer
import de.connect2x.trixnity.clientserverapi.model.discovery.GetSupport
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.keysOf
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

class DiscoveryRouteTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = EventContentSerializerMappings.default

    val handlerMock = mock<DiscoveryApiHandler>()

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
                discoveryApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetClient() = testApplication {
        initCut()
        everySuspend { handlerMock.getClient(any()) }
            .returns(
                DiscoveryInformation(
                    homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
                    identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
                )
            )
        val response = client.get("/.well-known/matrix/client")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """
                    {
                      "m.homeserver": {
                        "base_url": "https://matrix.example.com"
                      },
                      "m.identity_server": {
                        "base_url": "https://identity.example.com"
                      }
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getClient(any())
        }
    }

    @Test
    fun shouldGetSupport() = testApplication {
        initCut()
        everySuspend { handlerMock.getSupport(any()) }
            .returns(
                GetSupport.Response(
                    contacts = listOf(
                        GetSupport.Response.Contact(
                            emailAddress = "admin@example.org",
                            userId = UserId("@admin:example.org"),
                            role = GetSupport.Response.Contact.Role.Admin,
                        ),
                        GetSupport.Response.Contact(
                            emailAddress = "dino@example.org",
                            role = GetSupport.Response.Contact.Role.Unknown("m.role.dino"),
                        )
                    ),
                    supportPage = "https://example.org/support.html"
                )
            )
        val response = client.get("/.well-known/matrix/support")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """
                    {
                      "contacts": [
                        {
                          "email_address": "admin@example.org",
                          "matrix_id": "@admin:example.org",
                          "role": "m.role.admin"
                        },
                        {
                          "email_address": "dino@example.org",
                          "role": "m.role.dino"
                        }
                      ],
                      "support_page": "https://example.org/support.html"
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getSupport(any())
        }
    }

    @Test
    fun shouldGetPolicyServer() = testApplication {
        initCut()
        everySuspend { handlerMock.getPolicyServer(any()) }
            .returns(
                GetPolicyServer.Response(
                    publicKeys = keysOf(
                        Key.Ed25519Key(null, "6yhHGKhCiXTSEN2ksjV7kX_N6rBQZ3Xb-M7LlC6NS-s")
                    )
                )
            )
        val response = client.get("/.well-known/matrix/policy_server")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """
                    {
                      "public_keys": {
                        "ed25519": "6yhHGKhCiXTSEN2ksjV7kX_N6rBQZ3Xb-M7LlC6NS-s"
                      }
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPolicyServer(any())
        }
    }
}
