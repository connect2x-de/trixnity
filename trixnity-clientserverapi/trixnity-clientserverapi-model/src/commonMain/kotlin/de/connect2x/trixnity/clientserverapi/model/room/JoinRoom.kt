package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.17/client-server-api/#post_matrixclientv3roomsroomidjoin">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/join")
@HttpMethod(POST)
data class JoinRoom(@SerialName("roomId") val roomId: RoomId) : MatrixEndpoint<JoinRoom.Request, JoinRoom.Response> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String? = null,
        @SerialName("third_party_signed") val thirdPartySigned: ThirdPartySigned? = null,
    )

    @Serializable data class Response(@SerialName("room_id") val roomId: RoomId)
}
