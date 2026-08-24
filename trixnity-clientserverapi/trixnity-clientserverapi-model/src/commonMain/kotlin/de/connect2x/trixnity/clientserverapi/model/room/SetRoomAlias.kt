package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3directoryroomroomalias">matrix
 *   spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/room/{roomAliasId}")
@HttpMethod(PUT)
data class SetRoomAlias(@SerialName("roomAliasId") val roomAliasId: RoomAliasId) :
    MatrixEndpoint<SetRoomAlias.Request, Unit> {
    @Serializable data class Request(@SerialName("room_id") val roomId: RoomId)
}
