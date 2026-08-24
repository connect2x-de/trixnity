package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@MSC3814
@Serializable
data class DehydratedDeviceEventContent(@SerialName("encrypted") override val encrypted: Map<String, JsonElement>) :
    SecretEventContent
