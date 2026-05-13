package de.connect2x.trixnity.clientserverapi.model.admin

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.18/client-server-api/#put_matrixclientv1adminlockuserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/admin/lock/{userId}")
@HttpMethod(PUT)
data class SetLock(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<SetLock.Request, SetLock.Response> {
    @Serializable
    data class Request(
        @SerialName("locked") val locked: Boolean,
    )

    @Serializable
    data class Response(
        @SerialName("locked") val locked: Boolean,
    )
}
