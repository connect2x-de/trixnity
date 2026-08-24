package de.connect2x.trixnity.serverserverapi.model.discovery

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1version">matrix spec</a> */
@Serializable
@Resource("/_matrix/federation/v1/version")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetServerVersion : MatrixEndpoint<Unit, GetServerVersion.Response> {
    @Serializable
    data class Response(@SerialName("server") val server: Server) {
        @Serializable
        data class Server(@SerialName("name") val name: String, @SerialName("version") val version: String)
    }
}
