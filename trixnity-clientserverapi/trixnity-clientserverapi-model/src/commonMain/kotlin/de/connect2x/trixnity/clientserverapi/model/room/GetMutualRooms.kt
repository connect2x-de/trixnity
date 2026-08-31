package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/latest/client-server-api/#mutual-rooms">matrix spec</a> */
@Serializable
@Resource("/_matrix/client/v1/mutual_rooms")
@HttpMethod(GET)
@Auth(AuthRequired.YES)
data class GetMutualRooms(@SerialName("user_id") val userId: UserId, @SerialName("from") val from: String? = null) :
    MatrixEndpoint<Unit, GetMutualRooms.Response> {
    @Serializable
    data class Response(
        @SerialName("count") val count: Long,
        @SerialName("joined") val joined: Set<RoomId>,
        @SerialName("next_batch") val nextBatch: String? = null,
    )
}
