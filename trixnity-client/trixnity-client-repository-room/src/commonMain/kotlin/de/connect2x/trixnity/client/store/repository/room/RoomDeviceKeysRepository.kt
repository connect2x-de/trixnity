package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.repository.DeviceKeysRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "DeviceKeys")
data class RoomDeviceKeys(
    @PrimaryKey val userId: UserId,
    val value: String,
)

@Dao
interface DeviceKeysDao {
    @Query("SELECT * FROM DeviceKeys WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: UserId): RoomDeviceKeys?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomDeviceKeys)

    @Query("DELETE FROM DeviceKeys WHERE userId = :userId")
    suspend fun delete(userId: UserId)

    @Query("DELETE FROM DeviceKeys")
    suspend fun deleteAll()
}

internal class RoomDeviceKeysRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : DeviceKeysRepository {
    private val dao = db.deviceKeys()

    context(transaction: ReadTransaction)
    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString<Map<String, StoredDeviceKeys>>(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>) =
        dao.insert(
            RoomDeviceKeys(
                userId = key,
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: UserId) =
        dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
