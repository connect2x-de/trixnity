package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import kotlinx.serialization.Serializable

@Serializable data class StoredDeviceKeys(val value: SignedDeviceKeys, val trustLevel: KeySignatureTrustLevel)
