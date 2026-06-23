package de.connect2x.trixnity.serverserverapi.model.federation

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.keys.Signed
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1stateroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/state/{roomId}")
@HttpMethod(GET)
data class GetState(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("event_id") val eventId: EventId,
) : MatrixEndpoint<Unit, GetState.Response> {
    @Serializable
    data class Response(
        @SerialName("auth_chain")
        val authChain: List<Signed<@Contextual PersistentDataUnit<*>, String>>,
        @SerialName("pdus")
        val pdus: List<Signed<@Contextual PersistentDataUnit<*>, String>>
    )
}
