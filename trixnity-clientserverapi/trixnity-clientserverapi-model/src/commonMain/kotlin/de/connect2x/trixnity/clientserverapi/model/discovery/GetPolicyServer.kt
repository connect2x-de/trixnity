package de.connect2x.trixnity.clientserverapi.model.discovery

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.keys.Keys
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.18/client-server-api/#getwell-knownmatrixpolicy_server">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/policy_server")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetPolicyServer : MatrixEndpoint<Unit, GetPolicyServer.Response> {
    @Serializable
    data class Response(
        @SerialName("public_keys") val publicKeys: Keys
    )
}
