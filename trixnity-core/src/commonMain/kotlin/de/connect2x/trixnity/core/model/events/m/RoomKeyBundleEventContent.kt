package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/latest/client-server-api/#mroom_key_bundle">matrix spec</a> */
@Serializable
data class RoomKeyBundleEventContent(
    @SerialName("room_id") val roomId: RoomId,
    @SerialName("file") val file: EncryptedFile,
) : ToDeviceEventContent
