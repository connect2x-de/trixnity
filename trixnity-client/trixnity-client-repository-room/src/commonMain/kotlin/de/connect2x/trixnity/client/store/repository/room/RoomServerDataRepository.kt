package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.repository.ServerDataRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "ServerData")
data class RoomServerData(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface ServerDataDao {
    @Query("SELECT * FROM ServerData WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomServerData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomServerData)

    @Query("DELETE FROM ServerData WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ServerData")
    suspend fun deleteAll()
}

internal class RoomServerDataRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : ServerDataRepository {
    private val dao = db.serverData()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): ServerData? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: ServerData) =
        dao.insert(
            RoomServerData(
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
