package de.connect2x.trixnity.core.model.events.m.key.verification

import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationdone">matrix spec</a> */
@Serializable
data class VerificationDoneEventContent(
    @SerialName("m.relates_to") override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id") override val transactionId: String?,
) : VerificationStep {
    override val externalUrl: String? = null
    override val mentions: Mentions? = null

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo as? RelatesTo.Reference)
}
