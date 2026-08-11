package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.repository.NotificationRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(
    tableName = "Notification",
    primaryKeys = ["id"]
)
data class RoomNotification(
    val id: String,
    val roomId: RoomId,
    val value: String,
)

@Dao
interface NotificationDao {
    @Query("SELECT * FROM Notification")
    suspend fun getAll(): List<RoomNotification>

    @Query("SELECT * FROM Notification WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomNotification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomNotification)

    @Query("DELETE FROM Notification WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM Notification WHERE roomId = :roomId ")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM Notification")
    suspend fun deleteAll()
}

internal class RoomNotificationRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : NotificationRepository {

    private val dao = db.notification()

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredNotification> =
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): StoredNotification? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: StoredNotification) =
        dao.insert(
            RoomNotification(
                id = key,
                roomId = value.roomId,
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) =
        dao.delete(roomId)

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) =
        dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
