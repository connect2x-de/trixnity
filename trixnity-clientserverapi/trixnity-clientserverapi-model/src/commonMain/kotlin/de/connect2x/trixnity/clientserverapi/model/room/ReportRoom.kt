package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.13/client-server-api/#post_matrixclientv3roomsroomidreport">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/report")
@HttpMethod(POST)
data class ReportRoom(@SerialName("roomId") val roomId: RoomId) : MatrixEndpoint<ReportRoom.Request, Unit> {
    @Serializable data class Request(@SerialName("reason") val reason: String)
}
