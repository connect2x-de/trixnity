package de.connect2x.trixnity.clientserverapi.model.push

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.DELETE
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.push.PushRuleKind
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix
 *   spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}")
@HttpMethod(DELETE)
data class DeletePushRule(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: PushRuleKind,
    @SerialName("ruleId") val ruleId: String,
) : MatrixEndpoint<Unit, Unit>
