package de.connect2x.trixnity.clientserverapi.model.key

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3room_keysversionversion">matrix
 *   spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/version/{version}")
@HttpMethod(GET)
data class GetRoomKeyBackupVersionByVersion(@SerialName("version") val version: String) :
    MatrixEndpoint<Unit, GetRoomKeysBackupVersionResponse>
