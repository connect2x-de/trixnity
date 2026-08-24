package de.connect2x.trixnity.clientserverapi.model.device

import de.connect2x.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.DELETE
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#delete_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/devices/{deviceId}")
@HttpMethod(DELETE)
data class DeleteDevice(@SerialName("deviceId") val deviceId: String) : MatrixUIAEndpoint<Unit, Unit>
