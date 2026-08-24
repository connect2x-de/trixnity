package de.connect2x.trixnity.core.model.events.m.policy

import de.connect2x.trixnity.core.model.events.StateEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mpolicyruleroom">matrix spec</a> */
@Serializable
data class RoomRuleEventContent(
    @SerialName("entity") val entity: String,
    @SerialName("reason") val reason: String,
    @SerialName("recommendation") val recommendation: String,
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent
