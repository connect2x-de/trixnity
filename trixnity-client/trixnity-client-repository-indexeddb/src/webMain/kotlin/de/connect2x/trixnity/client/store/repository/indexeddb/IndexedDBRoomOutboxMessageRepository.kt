package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import web.idb.IDBDatabase

@Serializable
internal class IndexedDBRoomOutboxMessage<T : MessageEventContent>(
    val roomId: RoomId,
    val value: RoomOutboxMessage<T>,
    val contentType: String,
)

internal class IndexedDBRoomOutboxMessageRepository(
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
) : RoomOutboxMessageRepository, IndexedDBRepository(objectStoreName) {

    private val serializer =
        object : KSerializer<IndexedDBRoomOutboxMessage<*>> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IndexedDBRoomOutboxMessage")

            override fun deserialize(decoder: Decoder): IndexedDBRoomOutboxMessage<*> {
                require(decoder is JsonDecoder)
                val jsonObject = decoder.decodeJsonElement().jsonObject
                val contentType = jsonObject["contentType"]?.jsonPrimitive?.content
                val serializer = mappings.message.find { it.type == contentType }?.serializer
                checkNotNull(serializer)
                return json.decodeFromJsonElement(IndexedDBRoomOutboxMessage.serializer(serializer), jsonObject)
            }

            override fun serialize(encoder: Encoder, value: IndexedDBRoomOutboxMessage<*>) {
                require(encoder is JsonEncoder)
                val serializer = mappings.message.find { it.type == value.contentType }?.serializer
                checkNotNull(serializer)
                encoder.encodeJsonElement(
                    @Suppress("UNCHECKED_CAST")
                    encoder.json.encodeToJsonElement(
                        IndexedDBRoomOutboxMessage.serializer(serializer),
                        value as IndexedDBRoomOutboxMessage<MessageEventContent>,
                    )
                )
            }
        }

    private val internalRepository =
        object :
            IndexedDBFullRepository<RoomOutboxMessageRepositoryKey, IndexedDBRoomOutboxMessage<*>>(
                objectStoreName = objectStoreName,
                keySerializer = { arrayOf(it.roomId.full, it.transactionId) },
                valueSerializer = serializer,
                json = json,
            ) {
            override fun serializeKey(key: RoomOutboxMessageRepositoryKey): String =
                this@IndexedDBRoomOutboxMessageRepository.serializeKey(key)
        }

    companion object {
        const val objectStoreName = "room_outbox_message_2"

        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 6)
                createIndexedDBMinimalStoreRepository(database, objectStoreName) { store ->
                    store.createIndex("roomId", KeyPath.Single("roomId"), unique = false)
                }
        }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomOutboxMessageRepositoryKey): RoomOutboxMessage<*>? =
        internalRepository.get(key)?.value

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<RoomOutboxMessage<*>> = withRead { store ->
        store.openCursor().mapNotNull { json.decodeFromDynamicNullable(serializer, it.value) }.map { it.value }.toList()
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomOutboxMessageRepositoryKey, value: RoomOutboxMessage<*>) {
        val contentType = mappings.message.find { it.kClass.isInstance(value.content) }?.type
        checkNotNull(contentType)
        internalRepository.save(key, IndexedDBRoomOutboxMessage(key.roomId, value, contentType))
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomOutboxMessageRepositoryKey) = internalRepository.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = internalRepository.deleteAll()

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) = withWrite { store ->
        store.index("roomId").openCursor(keyOf(roomId.full)).collect { store.delete(it.primaryKey) }
    }
}
