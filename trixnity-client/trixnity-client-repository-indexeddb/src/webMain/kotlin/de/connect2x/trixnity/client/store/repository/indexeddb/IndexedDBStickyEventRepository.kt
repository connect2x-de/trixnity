package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlin.time.Instant
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import web.idb.IDBDatabase
import web.idb.IDBKeyRange

@Serializable
@MSC4354
internal class IndexedDBStickyEvent(
    val roomId: String,
    val eventId: String,
    val endTimeMs: Long,
    val value: StoredStickyEvent,
)

@OptIn(MSC4354::class)
internal class IndexedDBStickyEventRepository(json: Json) :
    StickyEventRepository,
    IndexedDBMapRepository<RoomId, EventId, StoredStickyEvent, IndexedDBStickyEvent>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "roomId",
        firstKeySerializer = { arrayOf(it.full) },
        secondKeySerializer = { arrayOf(it.full) },
        secondKeyDestructor = { EventId(it.eventId) },
        mapToRepresentation = { k1, k2, v ->
            IndexedDBStickyEvent(
                roomId = k1.full,
                eventId = k2.full,
                endTimeMs = v.endTime.toEpochMilliseconds(),
                value = v,
            )
        },
        mapFromRepresentation = { it.value },
        representationSerializer = IndexedDBStickyEvent.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "sticky_event_2"

        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 12)
                createIndexedDBTwoDimensionsStoreRepository(
                    database = database,
                    objectStoreName = objectStoreName,
                    keyPath = KeyPath.Multiple("roomId", "eventId"),
                    firstKeyIndexName = "roomId",
                    firstKeyIndexKeyPath = KeyPath.Single("roomId"),
                ) { store ->
                    store.createIndex("endTimeMs", KeyPath.Single("endTimeMs"), unique = false)
                }
        }
    }

    context(transaction: ReadTransaction)
    override suspend fun getByEndTimeBefore(before: Instant): Set<Pair<RoomId, EventId>> = withRead { store ->
        store
            .index("endTimeMs")
            .openCursor(IDBKeyRange.upperBound(before.toEpochMilliseconds().toDouble().toJsNumber(), true))
            .mapNotNull { json.decodeFromDynamicNullable(representationSerializer, it.value) }
            .map { RoomId(it.roomId) to EventId(it.eventId) }
            .toSet()
    }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredStickyEvent> = withRead { store ->
        store
            .openCursor()
            .mapNotNull { json.decodeFromDynamicNullable(representationSerializer, it.value) }
            .map { it.value }
            .toList()
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) = withWrite { store ->
        store.index("roomId").openCursor(keyOf(roomId.full)).collect { store.delete(it.primaryKey) }
    }
}
