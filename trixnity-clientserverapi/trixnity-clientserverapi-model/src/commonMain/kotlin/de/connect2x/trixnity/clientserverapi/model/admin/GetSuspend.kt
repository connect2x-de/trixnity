package de.connect2x.trixnity.clientserverapi.model.admin

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.18/client-server-api/#get_matrixclientv1adminsuspenduserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/admin/suspend/{userId}")
@HttpMethod(GET)
data class GetSuspend(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Unit, GetSuspend.Response> {
    @Serializable
    data class Response(
        @SerialName("suspended") val suspended: Boolean,
    )
}
