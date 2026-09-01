package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/latest/client-server-api/#mimage_packrooms">matrix spec</a> */
@Serializable
data class ImagePackRoomsEventContent(@SerialName("rooms") val rooms: Map<RoomId, Map<String, Unit>>) :
    GlobalAccountDataEventContent
