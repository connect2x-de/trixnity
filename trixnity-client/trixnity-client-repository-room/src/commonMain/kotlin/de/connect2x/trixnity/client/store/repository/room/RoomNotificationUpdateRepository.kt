package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(
    tableName = "NotificationUpdate",
    primaryKeys = ["id"]
)
data class RoomNotificationUpdate(
    val id: String,
    val roomId: RoomId,
    val value: String,
)

@Dao
interface NotificationUpdateDao {
    @Query("SELECT * FROM NotificationUpdate")
    suspend fun getAll(): List<RoomNotificationUpdate>

    @Query("SELECT * FROM NotificationUpdate WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomNotificationUpdate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomNotificationUpdate)

    @Query("DELETE FROM NotificationUpdate WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM NotificationUpdate WHERE roomId = :roomId ")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM NotificationUpdate")
    suspend fun deleteAll()
}

internal class RoomNotificationUpdateRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : NotificationUpdateRepository {

    private val dao = db.notificationUpdate()

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredNotificationUpdate> =
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): StoredNotificationUpdate? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: StoredNotificationUpdate) =
        dao.insert(
            RoomNotificationUpdate(
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
