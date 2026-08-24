package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.events.m.RoomKeyRequestEventContent
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class StoredRoomKeyRequest(
    val content: RoomKeyRequestEventContent,
    val receiverDeviceIds: Set<String>,
    val createdAt: Instant,
)
