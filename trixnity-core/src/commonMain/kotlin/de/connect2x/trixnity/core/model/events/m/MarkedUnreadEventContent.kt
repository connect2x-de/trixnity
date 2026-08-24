package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarkedUnreadEventContent(@SerialName("unread") val unread: Boolean) : RoomAccountDataEventContent
