package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class StoredOlmSession(
    val senderKey: Curve25519KeyValue,
    val sessionId: String,
    val lastUsedAt: Instant,
    val createdAt: Instant,
    val pickled: String,
    val initiatedByThisDevice: Boolean,
)
