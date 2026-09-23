package de.connect2x.trixnity.client.store.repository.room

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import de.connect2x.trixnity.client.store.StoredSecretKeyRequest
import de.connect2x.trixnity.client.store.repository.SecretKeyRequestRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "SecretKeyRequest") data class RoomSecretKeyRequest(@PrimaryKey val id: String, val value: String)

@Dao
interface SecretKeyRequestDao {
    @Query("SELECT * FROM SecretKeyRequest WHERE id = :id LIMIT 1") suspend fun get(id: String): RoomSecretKeyRequest?

    @Query("SELECT * FROM SecretKeyRequest") suspend fun getAll(): List<RoomSecretKeyRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomSecretKeyRequest)

    @Query("DELETE FROM SecretKeyRequest WHERE id = :id") suspend fun delete(id: String)

    @Query("DELETE FROM SecretKeyRequest") suspend fun deleteAll()
}

internal class RoomSecretKeyRequestRepository(db: TrixnityRoomDatabase, private val json: Json) :
    SecretKeyRequestRepository {

    private val dao = db.secretKeyRequest()

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): StoredSecretKeyRequest? =
        dao.get(key)?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredSecretKeyRequest> =
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: StoredSecretKeyRequest) =
        dao.insert(RoomSecretKeyRequest(id = key, value = json.encodeToString(value)))

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) = dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
