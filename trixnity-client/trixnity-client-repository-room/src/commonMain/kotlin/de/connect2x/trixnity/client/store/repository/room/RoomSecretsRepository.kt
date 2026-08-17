package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.client.store.repository.SecretsRepository
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "Secrets")
data class RoomSecrets(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface SecretsDao {
    @Query("SELECT * FROM Secrets WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomSecrets?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomSecrets)

    @Query("DELETE FROM Secrets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Secrets")
    suspend fun deleteAll()
}

internal class RoomSecretsRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : SecretsRepository {

    private val dao = db.secrets()

    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Map<SecretType, StoredSecret>? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Map<SecretType, StoredSecret>) =
        dao.insert(
            RoomSecrets(
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
