package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.repository.RoomRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "Room")
data class RoomRoom(
    @PrimaryKey val roomId: RoomId,
    val value: String,
)

@Dao
interface RoomRoomDao {
    @Query("SELECT * FROM Room WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: RoomId): RoomRoom?

    @Query("SELECT * FROM Room")
    suspend fun getAll(): List<RoomRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoom)

    @Query("DELETE FROM Room WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM Room")
    suspend fun deleteAll()
}

internal class RoomRoomRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomRepository {
    private val dao = db.room()

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): Room? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<Room> =
        dao.getAll()
            .map { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: Room) =
        dao.insert(
            RoomRoom(
                roomId = key,
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) =
        dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
