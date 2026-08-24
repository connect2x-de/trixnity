package de.connect2x.trixnity.serverserverapi.model.discovery

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/server-server-api/#getwell-knownmatrixserver">matrix spec</a> */
@Serializable
@Resource("/.well-known/matrix/server")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetWellKnown : MatrixEndpoint<Unit, GetWellKnown.Response> {
    @Serializable data class Response(@SerialName("m.server") val server: String)
}
