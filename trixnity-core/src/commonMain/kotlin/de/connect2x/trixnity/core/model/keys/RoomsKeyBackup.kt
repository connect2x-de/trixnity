package de.connect2x.trixnity.core.model.keys

import de.connect2x.trixnity.core.model.RoomId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class RoomsKeyBackup(@SerialName("rooms") val rooms: Map<RoomId, RoomKeyBackup>)
