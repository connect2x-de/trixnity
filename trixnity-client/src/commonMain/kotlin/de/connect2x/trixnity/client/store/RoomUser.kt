package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class RoomUser(
    val roomId: RoomId,
    val userId: UserId,
    val name: String,
    // TODO replace with MemberEventContent only (needs custom serializer for backwards compatibility)
    val event: @Contextual StateBaseEvent<MemberEventContent>,
)
