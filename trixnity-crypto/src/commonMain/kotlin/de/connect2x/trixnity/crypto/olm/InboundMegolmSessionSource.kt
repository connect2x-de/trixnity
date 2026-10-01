package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface InboundMegolmSessionSource {
    @Serializable @SerialName("creator") data object Creator : InboundMegolmSessionSource

    @Serializable
    @SerialName("backup")
    data class UnauthenticatedBackup(val forwardingKeyChain: List<KeyValue.Curve25519KeyValue>? = null) :
        InboundMegolmSessionSource

    @Serializable
    @SerialName("keyRequest")
    data class KeyRequest(val forwardingKeyChain: List<KeyValue.Curve25519KeyValue>) : InboundMegolmSessionSource

    @Serializable
    @SerialName("keyBundle")
    data class KeyBundle(val sender: Set<Sender>) : InboundMegolmSessionSource {
        @Serializable data class Sender(val userId: UserId, val deviceId: String)
    }
}
