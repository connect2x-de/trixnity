package de.connect2x.trixnity.core.model.events.m.secretstorage

import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#key-storage">matrix spec</a> */
@Serializable
data class DefaultSecretKeyEventContent(@SerialName("key") val key: String) : GlobalAccountDataEventContent
