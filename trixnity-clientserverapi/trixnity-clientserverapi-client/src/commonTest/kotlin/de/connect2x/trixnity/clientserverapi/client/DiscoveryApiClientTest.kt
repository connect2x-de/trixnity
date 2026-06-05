package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.discovery.DiscoveryInformation
import de.connect2x.trixnity.clientserverapi.model.discovery.GetPolicyServer
import de.connect2x.trixnity.clientserverapi.model.discovery.GetSupport
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.testutils.scopedMockEngine
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoveryApiClientTest : TrixnityBaseTest() {
    @Test
    fun shouldGetClient() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/.well-known/matrix/client", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "m.homeserver": {
                                "base_url": "https://matrix.example.com"
                              },
                              "m.identity_server": {
                                "base_url": "https://identity.example.com"
                              },
                              "org.example.custom.property": {
                                "app_url": "https://custom.app.example.org"
                              }
                            }

                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.getClient().getOrThrow() shouldBe DiscoveryInformation(
            homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
            identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
        )
    }

    @Test
    fun shouldGetClientRegardlessOfContentType() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/.well-known/matrix/client", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "m.homeserver": {
                                "base_url": "https://matrix.example.com"
                              },
                              "m.identity_server": {
                                "base_url": "https://identity.example.com"
                              },
                              "org.example.custom.property": {
                                "app_url": "https://custom.app.example.org"
                              }
                            }

                        """.trimIndent(),
                        HttpStatusCode.OK,
                    )
                }
            })
        matrixRestClient.discovery.getClient().getOrThrow() shouldBe DiscoveryInformation(
            homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
            identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
        )
    }

    @Test
    fun shouldGetSupport() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/.well-known/matrix/support", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.getSupport().getOrThrow() shouldBe GetSupport.Response(
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
    }

    @Test
    fun shouldGetPolicyServer() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/.well-known/matrix/policy_server", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "public_keys": {
                                "ed25519": "6yhHGKhCiXTSEN2ksjV7kX_N6rBQZ3Xb-M7LlC6NS-s"
                              }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.getPolicyServer().getOrThrow() shouldBe GetPolicyServer.Response(
            publicKeys = keysOf(
                Key.Ed25519Key(null, "6yhHGKhCiXTSEN2ksjV7kX_N6rBQZ3Xb-M7LlC6NS-s")
            )
        )
    }
}
