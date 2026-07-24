package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import de.connect2x.trixnity.core.model.UserId
import kotlinx.serialization.Serializable

@Serializable
data class RoomDisplayName(
    /**
     * If an explicit name is set for the room, use this.
     */
    val explicitName: String? = null,
    /**
     * The Matrix server can determine some users in the room as heroes which contribute to the room's name in case no
     * explicit name is set. If the server did not send any heroes information, heroes are determined locally from the
     * joined or left members.
     */
    val heroes: List<UserId> = emptyList(),
    /**
     * In case heroes are used, this is the number of other users in this room that are not explicitly named.
     */
    val otherUsersCount: Int = 0,
    /**
     * If we are the only active member in the room, this returns `true`, `false` otherwise.
     */
    val isEmpty: Boolean = false,
    internal val summary: RoomSummary?,
)
