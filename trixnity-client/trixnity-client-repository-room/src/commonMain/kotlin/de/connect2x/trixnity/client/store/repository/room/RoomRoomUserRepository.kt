package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.repository.RoomUserRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "RoomUser", primaryKeys = ["userId", "roomId"])
data class RoomRoomUser(val userId: UserId, val roomId: RoomId, val value: String)

@Dao
interface RoomUserDao {
    @Query("SELECT * FROM RoomUser WHERE userId = :userId AND roomId = :roomId LIMIT 1")
    suspend fun get(userId: UserId, roomId: RoomId): RoomRoomUser?

    @Query("SELECT * FROM RoomUser WHERE roomId = :roomId") suspend fun get(roomId: RoomId): List<RoomRoomUser>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomRoomUser)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entities: List<RoomRoomUser>)

    @Query("DELETE FROM RoomUser WHERE roomId = :roomId") suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM RoomUser WHERE roomId = :roomId AND userId = :userId")
    suspend fun delete(roomId: RoomId, userId: UserId)

    @Query("DELETE FROM RoomUser") suspend fun deleteAll()
}

internal class RoomRoomUserRepository(db: TrixnityRoomDatabase, private val json: Json) : RoomUserRepository {
    private val dao = db.roomUser()

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUser> =
        dao.get(firstKey).associate { entity -> entity.userId to json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUser? =
        dao.get(secondKey, firstKey)?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUser) =
        dao.insert(RoomRoomUser(userId = secondKey, roomId = firstKey, value = json.encodeToString(value)))

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) = dao.delete(roomId)

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomId, secondKey: UserId) = dao.delete(firstKey, secondKey)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
