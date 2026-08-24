package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class StoredOutboundMegolmSession(
    val roomId: RoomId,
    val createdAt: Instant,
    val encryptedMessageCount: Long,
    val newDevices: Map<UserId, Set<String>>,
    val pickled: String,
)
