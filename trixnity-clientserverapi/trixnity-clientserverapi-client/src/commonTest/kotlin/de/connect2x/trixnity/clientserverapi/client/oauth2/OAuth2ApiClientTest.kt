package de.connect2x.trixnity.clientserverapi.client.oauth2

import de.connect2x.trixnity.clientserverapi.client.trimToFlatJson
import de.connect2x.trixnity.clientserverapi.model.authentication.TokenTypeHint
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.CodeChallengeMethod
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.GrantType
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.PromptValue
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ResponseMode
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ResponseType
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.testutils.scopedMockEngine
import de.connect2x.trixnity.utils.nextString
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import kotlin.test.Test

class OAuth2ApiClientTest {

    private val serverMetadata = ServerMetadata(
        issuer = Url("https://auth.matrix.host"),
        authorizationEndpoint = Url("https://auth.matrix.host/authorize"),
        registrationEndpoint = Url("https://auth.matrix.host/registration"),
        revocationEndpoint = Url("https://auth.matrix.host/revoke"),
        tokenEndpoint = Url("https://auth.matrix.host/token"),
        codeChallengeMethodsSupported = setOf(CodeChallengeMethod.S256),
        responseTypesSupported = setOf(ResponseType.Code),
        responseModesSupported = setOf(ResponseMode.Query),
        promptValuesSupported = setOf(PromptValue.Create),
        grantTypesSupported = setOf(GrantType.RefreshToken, GrantType.AuthorizationCode),
        deviceAuthorizationEndpoint = Url("https://auth.matrix.host/device"),
    )

    @Test
    fun shouldRegisterClient() = runTest {
        val clientMetadata = ClientMetadata(
            applicationType = ApplicationType.Web,
            clientUri = "https://client.example.com",
            redirectUris = setOf("https://localhost:8080/redirect"),
            grantTypes = setOf(GrantType.RefreshToken, GrantType.AuthorizationCode),
            responseTypes = setOf(ResponseType.Code),
            tokenEndpointAuthMethod = TokenEndpointAuthMethod.None,
            clientName = LocalizedField("Trixnity", mapOf("de" to "Trixinity")),
        )

        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/registration"
                    request.body.toByteArray().decodeToString() shouldBe """
                        {
                            "application_type": "web",
                            "client_uri": "https://client.example.com",
                            "grant_types": [
                                "refresh_token",
                                "authorization_code"
                            ],
                            "redirect_uris": [
                                "https://localhost:8080/redirect"
                            ],
                            "response_types": [
                                "code"
                            ],
                            "token_endpoint_auth_method": "none",
                            "client_name": "Trixnity",
                            "client_name#de": "Trixinity"
                        }
                    """.trimToFlatJson()
                    request.body.contentType shouldBe ContentType.Application.Json
                    request.method shouldBe HttpMethod.Post

                    respond(
                        """
                            {
                                "client_id": "clientId",
                                "application_type": "web",
                                "client_uri": "https://client.example.com",
                                "grant_types": [
                                    "refresh_token",
                                    "authorization_code"
                                ],
                                "redirect_uris": [
                                    "https://localhost:8080/redirect"
                                ],
                                "response_types": [
                                    "code"
                                ],
                                "token_endpoint_auth_method": "none",
                                "client_name": "Trixnity",
                                "client_name#de": "Trixinity"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        )

        matrixRestClient.registerClient(clientMetadata).getOrThrow() shouldBe ClientRegistrationResponse(
            clientId = "clientId",
            clientMetadata = clientMetadata
        )
    }

    @Test
    fun shouldDeviceAuthorization() = runTest {
        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/device"
                    request.body.toByteArray()
                        .decodeToString() shouldBe
                            "client_id=clientId" +
                            "&scope=urn%3Amatrix%3Aclient%3Aapi%3A%2A+urn%3Amatrix%3Aclient%3Adevice%3AdeviceId"
                    request.body.contentType shouldBe ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8)
                    request.method shouldBe HttpMethod.Post

                    respond(
                        """
                                {
                                  "device_code": "DEVICE_CODE",
                                  "user_code": "USER_CODE",
                                  "verification_uri": "https://auth.matrix.host/verification",
                                  "verification_uri_complete": "https://auth.matrix.host/verification?user_code=USER_CODE",
                                  "expires_in": 1800,
                                  "interval": 6
                                }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        )

        matrixRestClient.deviceAuthorization(
            clientId = "clientId",
            deviceId = "deviceId",
        ).getOrThrow() shouldBe DeviceAuthorizationResponse(
            deviceCode = "DEVICE_CODE",
            userCode = "USER_CODE",
            verificationUri = Url("https://auth.matrix.host/verification"),
            verificationUriComplete = Url("https://auth.matrix.host/verification?user_code=USER_CODE"),
            expiresIn = 1800,
            interval = 6
        )
    }

    @Test
    fun shouldGetTokenByAuthorizationCode() = runTest {
        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/token"
                    request.body.toByteArray().decodeToString() shouldBe
                            "grant_type=authorization_code&code=CODE" +
                            "&redirect_uri=trixnity%3A%2F%2Fsso" +
                            "&client_id=clientId" +
                            "&code_verifier=CODE_VERIFIER"
                    request.body.contentType shouldBe ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8)
                    request.method shouldBe HttpMethod.Post

                    respond(
                        """
                            {
                                "access_token": "access",
                                "refresh_token": "refresh",
                                "token_type": "Bearer",
                                "expires_in": 3600,
                                "scope": "scope1 scope2"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        )

        matrixRestClient.getTokenByAuthorizationCode(
            clientId = "clientId",
            redirectUri = "trixnity://sso",
            code = "CODE",
            codeVerifier = "CODE_VERIFIER"
        ).getOrThrow() shouldBe TokenResponse(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "Bearer",
            expiresIn = 3600,
            scope = setOf(Scope.Unknown("scope1"), Scope.Unknown("scope2"))
        )
    }

    @Test
    fun shouldGetRefreshToken() = runTest {
        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/token"
                    request.body.toByteArray().decodeToString() shouldBe
                            "grant_type=refresh_token" +
                            "&refresh_token=refresh" +
                            "&client_id=clientId"
                    request.body.contentType shouldBe ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8)
                    request.method shouldBe HttpMethod.Post

                    respond(
                        """
                            {
                                "access_token": "access",
                                "refresh_token": "refresh",
                                "token_type": "Bearer",
                                "expires_in": 3600,
                                "scope": "scope1 scope2"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        )

        matrixRestClient.getRefreshToken(
            refreshToken = "refresh",
            clientId = "clientId",
        ).getOrThrow() shouldBe TokenResponse(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "Bearer",
            expiresIn = 3600,
            scope = setOf(Scope.Unknown("scope1"), Scope.Unknown("scope2"))
        )
    }

    @Test
    fun shouldRevokeToken() = runTest {
        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/revoke"
                    request.body.toByteArray().decodeToString() shouldBe
                            "token=refresh" +
                            "&token_type_hint=refresh_token" +
                            "&client_id=clientId"
                    request.body.contentType shouldBe ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8)
                    request.method shouldBe HttpMethod.Post

                    respond(
                        "",
                        HttpStatusCode.OK,
                    )
                }
            }
        )

        matrixRestClient.revokeToken(
            token = "refresh",
            tokenTypeHint = TokenTypeHint.RefreshToken,
            clientId = "clientId",
        ).getOrThrow()
    }

    @Test
    fun test() {
        val codeVerifier = SecureRandom.nextString(64)
        println(codeVerifier)
        val codeChallenge = codeVerifier.encodeToByteArray().toByteString().sha256().base64Url()
        println(codeChallenge)
    }
}
