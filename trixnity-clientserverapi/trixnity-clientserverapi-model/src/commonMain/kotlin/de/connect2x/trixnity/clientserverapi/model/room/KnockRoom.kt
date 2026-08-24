package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3knockroomidoralias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/knock/{roomIdOrRoomAliasId}")
@HttpMethod(POST)
data class KnockRoom(
    @SerialName("roomIdOrRoomAliasId") val roomIdOrRoomAliasId: String,
    @SerialName("via") val via: Set<String>? = null,
) : MatrixEndpoint<KnockRoom.Request, KnockRoom.Response> {
    @Serializable data class Request(@SerialName("reason") val reason: String? = null)

    @Serializable data class Response(@SerialName("room_id") val roomId: RoomId)
}
