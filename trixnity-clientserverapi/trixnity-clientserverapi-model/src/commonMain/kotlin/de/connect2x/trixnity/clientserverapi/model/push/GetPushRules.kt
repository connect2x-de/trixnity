package de.connect2x.trixnity.clientserverapi.model.push

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.push.PushRuleSet
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3pushrules">matrix spec</a> */
@Serializable
@Resource("/_matrix/client/v3/pushrules/")
@HttpMethod(GET)
data object GetPushRules : MatrixEndpoint<Unit, GetPushRules.Response> {
    @Serializable data class Response(@SerialName("global") val global: PushRuleSet)
}
