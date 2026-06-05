package de.connect2x.trixnity.core.model.events.m.room

import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.keys.Keys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PolicyEventContent(
    @SerialName("public_keys")
    val publicKeys: Keys,
    @SerialName("via")
    val via: String,
) : StateEventContent {
    override val externalUrl = null
}
