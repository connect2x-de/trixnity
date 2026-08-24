package de.connect2x.trixnity.clientserverapi.model.authentication

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3account3pidunbind">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid/unbind")
@HttpMethod(POST)
data object UnbindThirdPartyIdentifiers :
    MatrixEndpoint<UnbindThirdPartyIdentifiers.Request, UnbindThirdPartyIdentifiers.Response> {
    @Serializable
    data class Request(
        @SerialName("address") val address: String,
        @SerialName("id_server") val idServer: String? = null,
        @SerialName("medium") val medium: ThirdPartyIdentifier.Medium,
    )

    @Serializable
    data class Response(@SerialName("id_server_unbind_result") val idServerUnbindResult: IdServerUnbindResult)
}
