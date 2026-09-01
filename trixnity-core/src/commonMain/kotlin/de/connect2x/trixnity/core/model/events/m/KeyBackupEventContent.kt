package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/latest/client-server-api/#mkey_backup">matrix spec</a> */
@Serializable
data class KeyBackupEventContent(@SerialName("enabled") val enabled: Boolean) : GlobalAccountDataEventContent
