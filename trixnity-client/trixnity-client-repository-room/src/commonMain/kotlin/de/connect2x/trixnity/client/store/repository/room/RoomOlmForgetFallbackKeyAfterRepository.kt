package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlin.time.Instant

@Entity(tableName = "OlmForgetFallbackKeyAfter")
data class RoomOlmForgetFallbackKeyAfter(@PrimaryKey val id: Long, val instant: Instant)

@Dao
interface OlmForgetFallbackKeyAfterDao {
    @Query("SELECT * FROM OlmForgetFallbackKeyAfter WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomOlmForgetFallbackKeyAfter?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomOlmForgetFallbackKeyAfter)

    @Query("DELETE FROM OlmForgetFallbackKeyAfter WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM OlmForgetFallbackKeyAfter") suspend fun deleteAll()
}

internal class RoomOlmForgetFallbackKeyAfterRepository(db: TrixnityRoomDatabase) : OlmForgetFallbackKeyAfterRepository {
    private val dao = db.olmForgetFallbackKeyAfter()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Instant? = dao.get(key)?.instant

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Instant) =
        dao.insert(RoomOlmForgetFallbackKeyAfter(id = key, instant = value))

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) = dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
