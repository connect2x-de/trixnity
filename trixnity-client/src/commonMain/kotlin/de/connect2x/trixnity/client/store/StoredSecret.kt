package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretEventContent
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StoredSecret(
    @SerialName("event") val event: @Contextual GlobalAccountDataEvent<out SecretEventContent>,
    @SerialName("decryptedPrivateKey") val decryptedPrivateKey: String,
)
