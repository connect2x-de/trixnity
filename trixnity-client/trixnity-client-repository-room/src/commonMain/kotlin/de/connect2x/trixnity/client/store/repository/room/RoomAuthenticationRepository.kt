package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.Authentication
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "Authentication")
data class RoomAuthentication(@PrimaryKey val id: Long = 0, val value: String? = null)

@Dao
interface AuthenticationDao {
    @Query("SELECT * FROM Authentication WHERE id = :id LIMIT 1") suspend fun get(id: Long): RoomAuthentication?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomAuthentication)

    @Query("DELETE FROM Authentication WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM Authentication") suspend fun deleteAll()
}

internal class RoomAuthenticationRepository(db: TrixnityRoomDatabase, private val json: Json) :
    AuthenticationRepository {

    private val dao = db.authentication()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Authentication? = dao.get(key)?.value?.let { json.decodeFromString(it) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Authentication) =
        dao.insert(RoomAuthentication(id = key, value = json.encodeToString(value)))

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) = dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
