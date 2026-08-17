package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.Account
import de.connect2x.trixnity.client.store.repository.AccountRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "Account")
data class RoomAccount(
    @PrimaryKey val id: Long = 0,
    val olmPickleKey: String? = null,
    val baseUrl: String? = null,
    val userId: UserId? = null,
    val deviceId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val syncBatchToken: String? = null,
    val filter: String? = null,
    val profile: String? = null,
    val isLocked: Boolean = false,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM Account WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomAccount)

    @Query("DELETE FROM Account WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Account")
    suspend fun deleteAll()
}

internal class RoomAccountRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : AccountRepository {

    private val dao = db.account()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Account? =
        dao.get(key)?.let { entity ->
            Account(
                olmPickleKey = entity.olmPickleKey,
                baseUrl = entity.baseUrl,
                userId = entity.userId ?: throw IllegalStateException("userId not found"),
                deviceId = entity.deviceId ?: throw IllegalStateException("deviceId not found"),
                accessToken = entity.accessToken,
                refreshToken = entity.refreshToken,
                syncBatchToken = entity.syncBatchToken,
                filter = entity.filter?.let { json.decodeFromString(it) },
                profile = entity.profile?.let { json.decodeFromString(it) },
            )
        }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Account) =
        dao.insert(
            @Suppress("DEPRECATION")
            RoomAccount(
                id = key,
                olmPickleKey = value.olmPickleKey,
                baseUrl = value.baseUrl,
                userId = value.userId,
                deviceId = value.deviceId,
                accessToken = value.accessToken,
                refreshToken = value.refreshToken,
                syncBatchToken = value.syncBatchToken,
                filter = value.filter?.let { json.encodeToString(it) },
                profile = value.profile?.let { json.encodeToString(it) },
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) =
        dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
