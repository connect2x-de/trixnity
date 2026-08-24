package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.RoomStateRepository
import de.connect2x.trixnity.client.store.repository.RoomStateRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedRoomState : Table("room_state") {
    val roomId = varchar("room_id", length = 255)
    val type = varchar("type", length = 255)
    val stateKey = varchar("state_key", length = 255)
    override val primaryKey = PrimaryKey(roomId, type, stateKey)
    val event = text("event")
}

internal class ExposedRoomStateRepository(private val json: Json) : RoomStateRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer =
        json.serializersModule.getContextual(StateBaseEvent::class)
            ?: throw IllegalArgumentException("could not find event serializer")

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomStateRepositoryKey): Map<String, StateBaseEvent<*>> {
        return ExposedRoomState.selectAll()
            .where { ExposedRoomState.roomId.eq(firstKey.roomId.full) and ExposedRoomState.type.eq(firstKey.type) }
            .associate {
                it[ExposedRoomState.stateKey] to json.decodeFromString(serializer, it[ExposedRoomState.event])
            }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomStateRepositoryKey, secondKey: String): StateBaseEvent<*>? {
        return ExposedRoomState.selectAll()
            .where {
                ExposedRoomState.roomId.eq(firstKey.roomId.full) and
                    ExposedRoomState.type.eq(firstKey.type) and
                    ExposedRoomState.stateKey.eq(secondKey)
            }
            .firstOrNull()
            ?.let { json.decodeFromString(serializer, it[ExposedRoomState.event]) }
    }

    context(transaction: ReadTransaction)
    override suspend fun getByRooms(roomIds: Set<RoomId>, type: String, stateKey: String): List<StateBaseEvent<*>> {
        return ExposedRoomState.selectAll()
            .where {
                ExposedRoomState.roomId.inList(roomIds.map { it.full }) and
                    ExposedRoomState.type.eq(type) and
                    ExposedRoomState.stateKey.eq(stateKey)
            }
            .map { json.decodeFromString(serializer, it[ExposedRoomState.event]) }
            .toList()
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomState.deleteWhere { ExposedRoomState.roomId.eq(roomId.full) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomStateRepositoryKey, secondKey: String, value: StateBaseEvent<*>) {
        ExposedRoomState.upsert {
            it[roomId] = firstKey.roomId.full
            it[type] = firstKey.type
            it[stateKey] = secondKey
            it[event] = json.encodeToString(serializer, value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomStateRepositoryKey, secondKey: String) {
        ExposedRoomState.deleteWhere {
            roomId.eq(firstKey.roomId.full) and type.eq(firstKey.type) and stateKey.eq(secondKey)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedRoomState.deleteAll()
    }
}
