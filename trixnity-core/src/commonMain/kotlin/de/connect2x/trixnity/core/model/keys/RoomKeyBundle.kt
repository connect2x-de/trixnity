package de.connect2x.trixnity.core.model.keys

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomKeyBundle(
    @SerialName("room_keys") val roomKeys: List<HistoricRoomKey>? = null,
    @SerialName("withheld") val withheld: List<RoomKeyWhithheld>? = null,
) {
    @Serializable
    data class HistoricRoomKey(
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("sender_key") val senderKey: Curve25519KeyValue,
        @SerialName("sender_claimed_keys") val senderClaimedKeys: Keys,
        @SerialName("session_id") val sessionId: String,
        @SerialName("session_key") val sessionKey: ExportedSessionKeyValue,
        @SerialName("algorithm") val algorithm: EncryptionAlgorithm = EncryptionAlgorithm.Megolm,
    )

    @Serializable
    data class RoomKeyWhithheld(
        @SerialName("room_id") val roomId: RoomId? = null,
        @SerialName("sender_key") val senderKey: Curve25519KeyValue,
        @SerialName("session_id") val sessionId: String? = null,
        @SerialName("algorithm") val algorithm: EncryptionAlgorithm = EncryptionAlgorithm.Megolm,
        @SerialName("code") val code: RoomKeyWithheldCode,
        @SerialName("reason") val reason: String? = null,
    )
}
