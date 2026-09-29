package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RoomKeyBundleEventContent
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class StoredRoomKeyBundles(
    val roomId: RoomId,
    val acceptFrom: UserId?,
    val acceptUntil: Instant,
    val bundles: Set<Bundle>,
) {
    @Serializable
    data class Bundle(val senderUserId: UserId, val senderDeviceId: String, val content: RoomKeyBundleEventContent)
}
