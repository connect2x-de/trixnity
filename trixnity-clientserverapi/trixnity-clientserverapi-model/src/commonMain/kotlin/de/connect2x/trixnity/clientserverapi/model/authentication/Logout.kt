package de.connect2x.trixnity.clientserverapi.model.authentication

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3logout">matrix spec</a> */
@Serializable
@Resource("/_matrix/client/v3/logout")
@HttpMethod(POST)
data object Logout : MatrixEndpoint<Unit, Unit> {
    @Transient override val requestContentType: ContentType? = null
}
