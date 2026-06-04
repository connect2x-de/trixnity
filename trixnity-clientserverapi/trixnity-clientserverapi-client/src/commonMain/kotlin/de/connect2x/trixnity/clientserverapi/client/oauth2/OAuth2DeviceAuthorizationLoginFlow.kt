package de.connect2x.trixnity.clientserverapi.client.oauth2

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.GrantType
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.utils.nextString
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds


/**
 * Represents the flow for handling OAuth 2.0 device authorization based login operations.
 *
 * @see <a href="https://spec.matrix.org/v1.18/client-server-api/#device-authorization-flow">matrix spec</a>
 */
interface OAuth2DeviceAuthorizationLoginFlow {
    /**
     * Initiates the authentication process.
     */
    suspend fun createAuthRequest(): Result<AuthRequestData>

    /**
     * Represents the data required for initiating an authentication process.
     */
    data class AuthRequestData(
        val userCode: String,
        val verificationUri: Url,
        val verificationUriComplete: Url? = null,
    )

    /**
     * Wait and loop until device authentication is finished.
     */
    suspend fun waitForLogin(): Result<OAuth2MatrixClientAuthProviderData>
}


class OAuth2DeviceAuthorizationLoginFlowImpl(
    private val baseUrl: Url,
    private val applicationType: ApplicationType,
    private val clientUri: String,
    private val redirectUri: String,
    private val clientName: LocalizedField<String>? = null,
    private val logoUri: LocalizedField<String>? = null,
    private val policyUri: LocalizedField<String>? = null,
    private val tosUri: LocalizedField<String>? = null,
    private val httpClientEngine: HttpClientEngine? = null,
    private val httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : OAuth2DeviceAuthorizationLoginFlow {
    private val log =
        Logger("de.connect2x.trixnity.clientserverapi.client.oauth2.OAuth2DeviceAuthorizationLoginFlowImpl")
    private val deviceId = SecureRandom.nextString(10, alphabet = ('a'..'z') + ('A'..'Z'))

    private var clientMetadata: ClientRegistrationResponse? = null
    private var deviceAuthorization: DeviceAuthorizationResponse? = null

    private val getServerMetadata = object {
        private var cachedValue: ServerMetadata? = null
        suspend operator fun invoke() = cachedValue ?: run {
            MatrixClientServerApiClientImpl(
                baseUrl = baseUrl,
                httpClientEngine = httpClientEngine,
                httpClientConfig = httpClientConfig
            ).use {
                it.authentication.getOAuth2ServerMetadata().getOrThrow()
            }.also {
                cachedValue = it
            }
        }
    }

    override suspend fun createAuthRequest(): Result<OAuth2DeviceAuthorizationLoginFlow.AuthRequestData> = runCatching {
        OAuth2ApiClient(
            serverMetadata = getServerMetadata(),
            httpClientEngine = httpClientEngine,
            httpClientConfig = httpClientConfig,
        ).use {
            val clientMetadata = it.registerClient(
                ClientMetadata(
                    applicationType = applicationType,
                    clientUri = clientUri,
                    redirectUris = setOf(redirectUri),
                    clientName = clientName,
                    logoUri = logoUri,
                    policyUri = policyUri,
                    tosUri = tosUri,
                    grantTypes = setOf(GrantType.DeviceCode, GrantType.RefreshToken),
                    responseTypes = null,
                    tokenEndpointAuthMethod = TokenEndpointAuthMethod.None
                )
            ).getOrThrow()
            val deviceAuthorization =
                it.deviceAuthorization(clientId = clientMetadata.clientId, deviceId = deviceId).getOrThrow()

            this.clientMetadata = clientMetadata
            this.deviceAuthorization = deviceAuthorization
            OAuth2DeviceAuthorizationLoginFlow.AuthRequestData(
                userCode = deviceAuthorization.userCode,
                verificationUri = deviceAuthorization.verificationUri,
                verificationUriComplete = deviceAuthorization.verificationUriComplete,
            )
        }
    }

    override suspend fun waitForLogin(): Result<OAuth2MatrixClientAuthProviderData> = runCatching {
        val deviceAuthorization =
            checkNotNull(deviceAuthorization) { "you need to (successfully) call createAuthRequest first" }
        val clientMetadata = checkNotNull(clientMetadata) { "you need to (successfully) call createAuthRequest first" }
        OAuth2ApiClient(
            serverMetadata = getServerMetadata(),
            httpClientEngine = httpClientEngine,
            httpClientConfig = httpClientConfig,
        ).use {
            var interval = deviceAuthorization.interval.seconds
            val stopAfter = Clock.System.now() + deviceAuthorization.expiresIn.seconds
            val token = run<TokenResponse?> {
                while (currentCoroutineContext().isActive) {
                    if (stopAfter < Clock.System.now()) {
                        log.debug { "code expired" }
                        throw RuntimeException("code expired")
                    }
                    try {
                        return@run it.getTokenByDeviceCode(
                            clientId = clientMetadata.clientId,
                            deviceCode = deviceAuthorization.deviceCode
                        ).getOrThrow()
                    } catch (e: ClientRequestException) {
                        @Serializable
                        data class ErrorResponse(val error: String)

                        val error = e.response.body<ErrorResponse>().error
                        when (error) {
                            "authorization_pending" -> {
                                log.debug { "try again in $interval" }
                                delay(interval)
                            }

                            "slow_down" -> {
                                interval += 5.seconds
                                log.debug { "slow down to $interval" }
                                delay(interval)
                            }

                            else -> {
                                log.warn { "abort" }
                                throw e
                            }
                        }
                    }
                }
                null
            }
            checkNotNull(token) { "loop ended unexpectedly" }
            OAuth2MatrixClientAuthProviderData(
                baseUrl = baseUrl,
                accessToken = token.accessToken,
                accessTokenExpiresInS = token.expiresIn,
                refreshToken = token.refreshToken,
                clientId = clientMetadata.clientId,
                scope = token.scope
            )
        }
    }
}
