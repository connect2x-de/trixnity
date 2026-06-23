package de.connect2x.trixnity.serverserverapi.model.federation

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#put_matrixfederationv2send_joinroomideventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v2/send_join/{roomId}/{eventId}")
@HttpMethod(PUT)
data class SendJoin(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("omit_members") val omitMembers: Boolean? = null,
) : MatrixEndpoint<Signed<PersistentStateDataUnit<MemberEventContent>, String>, SendJoin.Response> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Signed<PersistentStateDataUnit<MemberEventContent>, String>?
    ): KSerializer<Signed<PersistentStateDataUnit<MemberEventContent>, String>> {
        @Suppress("UNCHECKED_CAST")
        val serializer = requireNotNull(json.serializersModule.getContextual(PersistentStateDataUnit::class))
                as KSerializer<PersistentStateDataUnit<MemberEventContent>>
        return Signed.serializer(serializer, String.serializer())
    }

    @Serializable
    data class Response(
        @SerialName("auth_chain")
        val authChain: List<Signed<@Contextual PersistentDataUnit<*>, String>>,
        @SerialName("event")
        val event: Signed<@Contextual PersistentStateDataUnit<MemberEventContent>, String>? = null,
        @SerialName("state")
        val state: List<Signed<@Contextual PersistentStateDataUnit<*>, String>>,
    )
}
