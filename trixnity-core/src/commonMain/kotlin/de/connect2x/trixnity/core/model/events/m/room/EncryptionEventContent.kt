package de.connect2x.trixnity.core.model.events.m.room

import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomencryption">matrix spec</a> */
@Serializable
data class EncryptionEventContent(
    @SerialName("rotation_period_ms") val rotationPeriodMs: Long? = null,
    @SerialName("rotation_period_msgs") val rotationPeriodMsgs: Long? = null,
    @SerialName("algorithm") val algorithm: EncryptionAlgorithm = EncryptionAlgorithm.Megolm,
) : StateEventContent {
    override val externalUrl: String? = null
}
