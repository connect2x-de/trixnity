package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.repository.OlmSessionRepository
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "OlmSession") data class RoomOlmSession(@PrimaryKey val senderKey: String, val value: String)

@Dao
interface OlmSessionDao {
    @Query("SELECT * FROM OlmSession WHERE senderKey = :senderKey LIMIT 1")
    suspend fun get(senderKey: String): RoomOlmSession?

    @Query("SELECT * FROM OlmSession") suspend fun getAll(): List<RoomOlmSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomOlmSession)

    @Query("DELETE FROM OlmSession WHERE senderKey = :senderKey") suspend fun delete(senderKey: String)

    @Query("DELETE FROM OlmSession") suspend fun deleteAll()
}

internal class RoomOlmSessionRepository(db: TrixnityRoomDatabase, private val json: Json) : OlmSessionRepository {
    private val dao = db.olmSession()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Curve25519KeyValue): Set<StoredOlmSession>? =
        dao.get(key.value)?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<Set<StoredOlmSession>> =
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Curve25519KeyValue, value: Set<StoredOlmSession>) =
        dao.insert(RoomOlmSession(senderKey = key.value, value = json.encodeToString(value)))

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Curve25519KeyValue) = dao.delete(key.value)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
