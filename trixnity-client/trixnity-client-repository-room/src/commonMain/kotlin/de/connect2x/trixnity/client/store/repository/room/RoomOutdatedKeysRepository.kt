package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.repository.OutdatedKeysRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "OutdatedKeys")
data class RoomOutdatedKeys(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface OutdatedKeysDao {
    @Query("SELECT * FROM OutdatedKeys WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomOutdatedKeys?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomOutdatedKeys)

    @Query("DELETE FROM OutdatedKeys WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM OutdatedKeys")
    suspend fun deleteAll()
}

internal class RoomOutdatedKeysRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : OutdatedKeysRepository {
    private val dao = db.outdatedKeys()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Set<UserId>? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Set<UserId>) =
        dao.insert(
            RoomOutdatedKeys(
                id = key,
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) =
        dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
