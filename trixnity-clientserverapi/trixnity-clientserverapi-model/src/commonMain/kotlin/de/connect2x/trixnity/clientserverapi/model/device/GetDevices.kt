package de.connect2x.trixnity.clientserverapi.model.device

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3devices">matrix spec</a> */
@Serializable
@Resource("/_matrix/client/v3/devices")
@HttpMethod(GET)
data object GetDevices : MatrixEndpoint<Unit, GetDevices.Response> {
    @Serializable data class Response(@SerialName("devices") val devices: List<Device>)
}
