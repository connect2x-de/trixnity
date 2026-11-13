package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.19/client-server-api/#mroom_keywithheld">matrix spec</a> */
@Serializable
data class RoomKeyWithheldEventContent(
    @SerialName("room_id") val roomId: RoomId? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("sender_key") val senderKey: KeyValue.Curve25519KeyValue,
    @SerialName("algorithm") val algorithm: EncryptionAlgorithm,
    @SerialName("code") val code: RoomKeyWithheldCode,
    @SerialName("reason") val reason: String? = null,
) : ToDeviceEventContent
