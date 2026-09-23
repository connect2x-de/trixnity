package de.connect2x.trixnity.client.store.repository.room

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import de.connect2x.trixnity.client.store.repository.OlmAccountRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

@Entity(tableName = "OlmAccount") data class RoomOlmAccount(@PrimaryKey val id: Long, val pickled: String)

@Dao
interface OlmAccountDao {
    @Query("SELECT * FROM OlmAccount WHERE id = :id LIMIT 1") suspend fun get(id: Long): RoomOlmAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomOlmAccount)

    @Query("DELETE FROM OlmAccount WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM OlmAccount") suspend fun deleteAll()
}

internal class RoomOlmAccountRepository(db: TrixnityRoomDatabase) : OlmAccountRepository {
    private val dao = db.olmAccount()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): String? = dao.get(key)?.pickled

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: String) = dao.insert(RoomOlmAccount(id = key, pickled = value))

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) = dao.delete(id = key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
