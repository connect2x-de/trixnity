package de.connect2x.trixnity.core.model.events

import de.connect2x.trixnity.core.model.RoomId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Deprecated("use PlaintextMegolmEvent instead", ReplaceWith("PlaintextMegolmEvent<C>"))
typealias DecryptedMegolmEvent<C> = PlaintextMegolmEvent<C>

@Serializable
data class PlaintextMegolmEvent<C : MessageEventContent>(
    @SerialName("content") override val content: C,
    @SerialName("room_id") val roomId: RoomId,
) : Event<C>
