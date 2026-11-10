package de.connect2x.trixnity.client.store.repository.room

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlin.time.Instant
import kotlinx.serialization.json.Json

@Entity(tableName = "StickyEvent2", primaryKeys = ["roomId", "eventId"])
data class RoomStickyEvent(val roomId: RoomId, val eventId: EventId, val endTimeMs: Long, val value: String)

@Dao
interface StickyEventDao {
    @Query("SELECT * FROM StickyEvent2 WHERE roomId = :roomId") suspend fun get(roomId: RoomId): List<RoomStickyEvent>

    @Query("SELECT * FROM StickyEvent2 WHERE roomId = :roomId AND eventId = :eventId LIMIT 1")
    suspend fun get(roomId: RoomId, eventId: EventId): RoomStickyEvent?

    @Query("SELECT * FROM StickyEvent2 WHERE endTimeMs < :before")
    suspend fun getByEndTimeBefore(before: Long): List<RoomStickyEvent>

    @Query("SELECT * FROM StickyEvent2") suspend fun getAll(): List<RoomStickyEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomStickyEvent)

    @Query("DELETE FROM StickyEvent2 WHERE roomId = :roomId AND eventId = :eventId")
    suspend fun delete(roomId: RoomId, eventId: EventId)

    @Query("DELETE FROM StickyEvent2") suspend fun deleteAll()

    @Query("DELETE FROM StickyEvent2 WHERE roomId = :roomId") suspend fun deleteByRoomId(roomId: RoomId)
}

@MSC4354
internal class RoomStickyEventRepository(db: TrixnityRoomDatabase, private val json: Json) : StickyEventRepository {
    private val dao = db.stickyRoomEvent()

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId): Map<EventId, StoredStickyEvent> =
        dao.get(firstKey).associate { it.eventId to json.decodeFromString(it.value) }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId, secondKey: EventId): StoredStickyEvent? =
        dao.get(firstKey, secondKey)?.let { json.decodeFromString(it.value) }

    context(transaction: ReadTransaction)
    override suspend fun getByEndTimeBefore(before: Instant): Set<Pair<RoomId, EventId>> =
        dao.getByEndTimeBefore(before.toEpochMilliseconds()).map { it.roomId to it.eventId }.toSet()

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredStickyEvent> = dao.getAll().map { json.decodeFromString(it.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomId, secondKey: EventId, value: StoredStickyEvent): Unit =
        dao.insert(
            RoomStickyEvent(
                roomId = firstKey,
                eventId = secondKey,
                endTimeMs = value.endTime.toEpochMilliseconds(),
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomId, secondKey: EventId): Unit = dao.delete(firstKey, secondKey)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll(): Unit = dao.deleteAll()

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId): Unit = dao.deleteByRoomId(roomId)
}
