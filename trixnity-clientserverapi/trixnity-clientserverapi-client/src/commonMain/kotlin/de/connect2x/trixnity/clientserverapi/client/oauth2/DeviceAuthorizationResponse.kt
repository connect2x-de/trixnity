package de.connect2x.trixnity.clientserverapi.client.oauth2

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceAuthorizationResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: Url,
    @SerialName("verification_uri_complete") val verificationUriComplete: Url? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("interval") val interval: Long = 5,
)
