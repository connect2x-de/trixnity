package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.keys.SignedCrossSigningKeys
import kotlinx.serialization.Serializable

@Serializable
data class StoredCrossSigningKeys(val value: SignedCrossSigningKeys, val trustLevel: KeySignatureTrustLevel)
