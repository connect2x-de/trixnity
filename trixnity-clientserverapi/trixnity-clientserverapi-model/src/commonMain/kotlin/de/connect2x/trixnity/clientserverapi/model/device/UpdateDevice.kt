package de.connect2x.trixnity.clientserverapi.model.device

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a> */
@Serializable
@Resource("/_matrix/client/v3/devices/{deviceId}")
@HttpMethod(PUT)
data class UpdateDevice(@SerialName("deviceId") val deviceId: String) : MatrixEndpoint<UpdateDevice.Request, Unit> {
    @Serializable data class Request(@SerialName("display_name") val displayName: String)
}
