package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepository
import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedRoomAccountData : Table("room_account_data") {
    val roomId = varchar("room_id", length = 255)
    val type = varchar("type", length = 255)
    val key = varchar("key", length = 255)
    override val primaryKey = PrimaryKey(roomId, type, key)
    val event = text("event")
}

internal class ExposedRoomAccountDataRepository(private val json: Json) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer =
        json.serializersModule.getContextual(RoomAccountDataEvent::class)
            ?: throw IllegalArgumentException("could not find event serializer")

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomAccountDataRepositoryKey): Map<String, RoomAccountDataEvent<*>> =
        ExposedRoomAccountData.selectAll()
            .where {
                ExposedRoomAccountData.roomId.eq(firstKey.roomId.full) and ExposedRoomAccountData.type.eq(firstKey.type)
            }
            .associate {
                it[ExposedRoomAccountData.key] to json.decodeFromString(serializer, it[ExposedRoomAccountData.event])
            }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomAccountData.deleteWhere { ExposedRoomAccountData.roomId.eq(roomId.full) }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomAccountDataRepositoryKey, secondKey: String): RoomAccountDataEvent<*>? {
        return ExposedRoomAccountData.selectAll()
            .where {
                ExposedRoomAccountData.roomId.eq(firstKey.roomId.full) and
                    ExposedRoomAccountData.type.eq(firstKey.type) and
                    ExposedRoomAccountData.key.eq(secondKey)
            }
            .firstOrNull()
            ?.let { json.decodeFromString(serializer, it[ExposedRoomAccountData.event]) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
        value: RoomAccountDataEvent<*>,
    ) {
        ExposedRoomAccountData.upsert {
            it[roomId] = firstKey.roomId.full
            it[type] = firstKey.type
            it[key] = secondKey
            it[event] = json.encodeToString(serializer, value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomAccountDataRepositoryKey, secondKey: String) {
        ExposedRoomAccountData.deleteWhere {
            roomId.eq(firstKey.roomId.full) and type.eq(firstKey.type) and key.eq(secondKey)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedRoomAccountData.deleteAll()
    }
}
