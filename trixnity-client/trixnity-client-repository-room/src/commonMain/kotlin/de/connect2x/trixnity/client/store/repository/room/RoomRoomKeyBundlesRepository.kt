package de.connect2x.trixnity.client.store.repository.room

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import de.connect2x.trixnity.client.store.StoredRoomKeyBundles
import de.connect2x.trixnity.client.store.repository.RoomKeyBundlesRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "RoomKeyBundles") data class RoomRoomKeyBundles(@PrimaryKey val roomId: RoomId, val value: String)

@Dao
interface RoomKeyBundlesDao {
    @Query("SELECT * FROM RoomKeyBundles WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: RoomId): RoomRoomKeyBundles?

    @Query("SELECT * FROM RoomKeyBundles") suspend fun getAll(): List<RoomRoomKeyBundles>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomRoomKeyBundles)

    @Query("DELETE FROM RoomKeyBundles WHERE roomId = :roomId") suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM RoomKeyBundles") suspend fun deleteAll()
}

internal class RoomRoomKeyBundlesRepository(db: TrixnityRoomDatabase, private val json: Json) :
    RoomKeyBundlesRepository {
    private val dao = db.roomKeyBundles()

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): StoredRoomKeyBundles? =
        dao.get(key)?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredRoomKeyBundles> =
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: StoredRoomKeyBundles) =
        dao.insert(RoomRoomKeyBundles(roomId = key, value = json.encodeToString(value)))

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) = dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
