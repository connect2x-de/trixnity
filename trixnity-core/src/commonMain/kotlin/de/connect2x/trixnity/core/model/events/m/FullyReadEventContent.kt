package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FullyReadEventContent(@SerialName("event_id") val eventId: EventId) : RoomAccountDataEventContent
