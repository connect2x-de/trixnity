package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredCrossSigningKeys
import de.connect2x.trixnity.client.store.repository.CrossSigningKeysRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "CrossSigningKeys")
data class RoomCrossSigningKeys(
    @PrimaryKey val userId: UserId,
    val value: String,
)

@Dao
interface CrossSigningKeysDao {
    @Query("SELECT * FROM CrossSigningKeys WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: UserId): RoomCrossSigningKeys?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomCrossSigningKeys)

    @Query("DELETE FROM CrossSigningKeys WHERE userId = :userId")
    suspend fun delete(userId: UserId)

    @Query("DELETE FROM CrossSigningKeys")
    suspend fun deleteAll()
}

internal class RoomCrossSigningKeysRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : CrossSigningKeysRepository {
    private val dao = db.crossSigningKeys()

    context(transaction: ReadTransaction)
    override suspend fun get(key: UserId): Set<StoredCrossSigningKeys>? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString<Set<StoredCrossSigningKeys>>(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKeys>) =
        dao.insert(
            RoomCrossSigningKeys(
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
