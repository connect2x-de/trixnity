package de.connect2x.trixnity.clientserverapi.model.authentication

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3account3pid">matrix spec</a> */
@Serializable
@Resource("/_matrix/client/v3/account/3pid")
@HttpMethod(GET)
data object GetThirdPartyIdentifiers : MatrixEndpoint<Unit, GetThirdPartyIdentifiers.Response> {
    @Serializable data class Response(@SerialName("threepids") val thirdPartyIdentifiers: Set<ThirdPartyIdentifier>)
}
