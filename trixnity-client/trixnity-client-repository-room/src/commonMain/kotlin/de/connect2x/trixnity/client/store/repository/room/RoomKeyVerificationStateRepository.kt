package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateKey
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateRepository
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(
    tableName = "KeyVerificationState",
    primaryKeys = ["keyId", "keyAlgorithm"]
)
data class RoomKeyVerificationState(
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm,
    val verificationState: String,
)

@Dao
interface KeyVerificationStateDao {
    @Query("SELECT * FROM KeyVerificationState WHERE keyId = :keyId AND keyAlgorithm = :keyAlgorithm LIMIT 1")
    suspend fun get(keyId: String, keyAlgorithm: KeyAlgorithm): RoomKeyVerificationState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomKeyVerificationState)

    @Query("DELETE FROM KeyVerificationState WHERE keyId = :keyId AND keyAlgorithm = :keyAlgorithm")
    suspend fun delete(keyId: String, keyAlgorithm: KeyAlgorithm)

    @Query("DELETE FROM KeyVerificationState")
    suspend fun deleteAll()
}

internal class RoomKeyVerificationStateRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : KeyVerificationStateRepository {
    private val dao = db.keyVerificationState()

    context(transaction: ReadTransaction)
    override suspend fun get(key: KeyVerificationStateKey): KeyVerificationState? =
        dao.get(key.keyId, key.keyAlgorithm)
            ?.let { json.decodeFromString(it.verificationState) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: KeyVerificationStateKey, value: KeyVerificationState) =
        dao.insert(
            RoomKeyVerificationState(
                keyId = key.keyId,
                keyAlgorithm = key.keyAlgorithm,
                verificationState = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: KeyVerificationStateKey) =
        dao.delete(key.keyId, key.keyAlgorithm)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
