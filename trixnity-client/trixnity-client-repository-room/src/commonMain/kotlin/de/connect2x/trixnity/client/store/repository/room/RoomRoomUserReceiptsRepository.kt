package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.repository.RoomUserReceiptsRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "RoomUserReceipts", primaryKeys = ["userId", "roomId"])
data class RoomRoomUserReceipts(val userId: UserId, val roomId: RoomId, val value: String)

@Dao
interface RoomUserReceiptsDao {
    @Query("SELECT * FROM RoomUserReceipts WHERE userId = :userId AND roomId = :roomId LIMIT 1")
    suspend fun get(userId: UserId, roomId: RoomId): RoomRoomUserReceipts?

    @Query("SELECT * FROM RoomUserReceipts WHERE roomId = :roomId")
    suspend fun get(roomId: RoomId): List<RoomRoomUserReceipts>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomRoomUserReceipts)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entities: List<RoomRoomUserReceipts>)

    @Query("DELETE FROM RoomUserReceipts WHERE roomId = :roomId") suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM RoomUserReceipts WHERE roomId = :roomId AND userId = :userId")
    suspend fun delete(roomId: RoomId, userId: UserId)

    @Query("DELETE FROM RoomUserReceipts") suspend fun deleteAll()
}

internal class RoomRoomUserReceiptsRepository(db: TrixnityRoomDatabase, private val json: Json) :
    RoomUserReceiptsRepository {
    private val dao = db.roomUserReceipts()

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUserReceipts> =
        dao.get(firstKey).associate { entity -> entity.userId to json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUserReceipts? =
        dao.get(secondKey, firstKey)?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUserReceipts) =
        dao.insert(RoomRoomUserReceipts(userId = secondKey, roomId = firstKey, value = json.encodeToString(value)))

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) = dao.delete(roomId)

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomId, secondKey: UserId) = dao.delete(firstKey, secondKey)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
