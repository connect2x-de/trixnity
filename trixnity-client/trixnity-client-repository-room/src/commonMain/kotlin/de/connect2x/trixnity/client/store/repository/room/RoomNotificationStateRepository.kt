package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredNotificationState
import de.connect2x.trixnity.client.store.repository.NotificationStateRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(
    tableName = "NotificationState",
    primaryKeys = ["roomId"]
)
data class RoomNotificationState(
    val roomId: RoomId,
    val value: String,
)

@Dao
interface NotificationStateDao {
    @Query("SELECT * FROM NotificationState")
    suspend fun getAll(): List<RoomNotificationState>

    @Query("SELECT * FROM NotificationState WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: RoomId): RoomNotificationState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomNotificationState)

    @Query("DELETE FROM NotificationState WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM NotificationState")
    suspend fun deleteAll()
}

internal class RoomNotificationStateRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : NotificationStateRepository {

    private val dao = db.notificationState()

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredNotificationState> = dao.getAll().map { entity ->
        json.decodeFromString(entity.value)
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): StoredNotificationState? = dao.get(key)
        ?.let { entity ->
            json.decodeFromString(entity.value)
        }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: StoredNotificationState) = dao.insert(
        RoomNotificationState(
            roomId = value.roomId,
            value = json.encodeToString(value),
        )
    )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) = dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
