package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.repository.TimelineEventKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRepository
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(
    tableName = "TimelineEvent",
    primaryKeys = ["roomId", "eventId"]
)
data class RoomTimelineEvent(
    val roomId: RoomId,
    val eventId: EventId,
    val value: String,
)

@Dao
interface TimelineEventDao {
    @Query("SELECT * FROM TimelineEvent WHERE roomId = :roomId AND eventId = :eventId LIMIT 1")
    suspend fun get(roomId: RoomId, eventId: EventId): RoomTimelineEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomTimelineEvent)

    @Query("DELETE FROM TimelineEvent WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM TimelineEvent WHERE roomId = :roomId AND eventId = :eventId")
    suspend fun delete(roomId: RoomId, eventId: EventId)

    @Query("DELETE FROM TimelineEvent")
    suspend fun deleteAll()
}

internal class RoomTimelineEventRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : TimelineEventRepository {

    private val dao = db.timelineEvent()

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) =
        dao.delete(roomId)

    context(transaction: ReadTransaction)
    override suspend fun get(key: TimelineEventKey): TimelineEvent? =
        dao.get(key.roomId, key.eventId)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: TimelineEventKey, value: TimelineEvent) =
        dao.insert(
            RoomTimelineEvent(
                roomId = key.roomId,
                eventId = key.eventId,
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: TimelineEventKey) =
        dao.delete(key.roomId, key.eventId)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
