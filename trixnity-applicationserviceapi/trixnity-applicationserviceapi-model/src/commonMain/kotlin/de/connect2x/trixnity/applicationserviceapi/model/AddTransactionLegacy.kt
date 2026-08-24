package de.connect2x.trixnity.applicationserviceapi.model

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/application-service-api/#put_matrixappv1transactionstxnid">matrix
 *   spec</a>
 */
@Serializable
@Resource("/transactions/{txnId}")
@HttpMethod(PUT)
data class AddTransactionLegacy(@SerialName("txnId") val txnId: String) :
    MatrixEndpoint<AddTransactionLegacy.Request, Unit> {
    @Serializable data class Request(@SerialName("events") val events: List<@Contextual RoomEvent<*>>)
}
