package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.repository.UserPresenceRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(
    tableName = "UserPresence",
    primaryKeys = ["userId"],
)
data class RoomUserPresence(
    val userId: UserId,
    val value: String,
)

@Dao
interface UserPresenceDao {
    @Query("SELECT * FROM UserPresence WHERE userId = :userId  LIMIT 1")
    suspend fun get(userId: UserId): RoomUserPresence?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomUserPresence)

    @Query("DELETE FROM UserPresence WHERE userId = :userId")
    suspend fun delete(userId: UserId)

    @Query("DELETE FROM UserPresence")
    suspend fun deleteAll()
}

internal class RoomUserPresenceRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : UserPresenceRepository {
    private val dao = db.userPresence()

    context(transaction: ReadTransaction)
    override suspend fun get(key: UserId): UserPresence? =
        dao.get(key)?.let { json.decodeFromString(it.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(
        key: UserId,
        value: UserPresence
    ) =
        dao.insert(
            RoomUserPresence(
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
