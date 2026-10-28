package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@MSC4354 @Serializable data class StoredStickyEvent(val roomId: RoomId, val eventId: EventId, val endTime: Instant)
