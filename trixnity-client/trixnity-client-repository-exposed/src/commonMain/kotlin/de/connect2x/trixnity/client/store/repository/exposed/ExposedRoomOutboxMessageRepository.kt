package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedRoomOutboxMessage : Table("room_outbox_2") {
    val transactionId = varchar("transaction_id", length = 255)
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(roomId, transactionId)
    val value = text("value")
    val contentType = text("content_type")
}

internal class ExposedRoomOutboxMessageRepository(
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
) : RoomOutboxMessageRepository {
    private fun mapToRoomOutboxMessage(input: ResultRow): RoomOutboxMessage<*> {
        val serializer = mappings.message.find { it.type == input[ExposedRoomOutboxMessage.contentType] }?.serializer
        requireNotNull(serializer)
        return json.decodeFromString(RoomOutboxMessage.serializer(serializer), input[ExposedRoomOutboxMessage.value])
    }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<RoomOutboxMessage<*>> {
        return ExposedRoomOutboxMessage.selectAll().map(::mapToRoomOutboxMessage).toList()
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomOutboxMessageRepositoryKey): RoomOutboxMessage<*>? {
        return ExposedRoomOutboxMessage.selectAll()
            .where {
                ExposedRoomOutboxMessage.roomId.eq(key.roomId.full) and
                    ExposedRoomOutboxMessage.transactionId.eq(key.transactionId)
            }
            .firstOrNull()
            ?.let(::mapToRoomOutboxMessage)
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomOutboxMessageRepositoryKey, value: RoomOutboxMessage<*>) {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)
        ExposedRoomOutboxMessage.upsert {
            it[roomId] = key.roomId.full
            it[transactionId] = key.transactionId
            @Suppress("UNCHECKED_CAST")
            it[ExposedRoomOutboxMessage.value] =
                json.encodeToString(
                    RoomOutboxMessage.serializer(mapping.serializer),
                    value as RoomOutboxMessage<MessageEventContent>,
                )
            it[contentType] = mapping.type
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomOutboxMessageRepositoryKey) {
        ExposedRoomOutboxMessage.deleteWhere { roomId.eq(key.roomId.full) and transactionId.eq(key.transactionId) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedRoomOutboxMessage.deleteAll()
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomOutboxMessage.deleteWhere { ExposedRoomOutboxMessage.roomId.eq(roomId.full) }
    }
}
