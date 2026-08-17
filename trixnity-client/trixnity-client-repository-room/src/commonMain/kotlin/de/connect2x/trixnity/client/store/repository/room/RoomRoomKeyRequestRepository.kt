package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredRoomKeyRequest
import de.connect2x.trixnity.client.store.repository.RoomKeyRequestRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "RoomKeyRequest")
data class RoomRoomKeyRequest(
    @PrimaryKey val id: String,
    val value: String,
)

@Dao
interface RoomKeyRequestDao {
    @Query("SELECT * FROM RoomKeyRequest WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomRoomKeyRequest?

    @Query("SELECT * FROM RoomKeyRequest")
    suspend fun getAll(): List<RoomRoomKeyRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomKeyRequest)

    @Query("DELETE FROM RoomKeyRequest WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM RoomKeyRequest")
    suspend fun deleteAll()
}

internal class RoomRoomKeyRequestRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomKeyRequestRepository {

    private val dao = db.roomKeyRequest()

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): StoredRoomKeyRequest? =
        dao.get(key)?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredRoomKeyRequest> =
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: StoredRoomKeyRequest) =
        dao.insert(
            RoomRoomKeyRequest(
                id = key,
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) =
        dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
