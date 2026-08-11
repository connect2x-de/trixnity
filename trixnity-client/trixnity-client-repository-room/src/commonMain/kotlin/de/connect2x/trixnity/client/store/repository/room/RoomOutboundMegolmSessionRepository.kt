package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.repository.OutboundMegolmSessionRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.json.Json

@Entity(tableName = "OutboundMegolmSession")
data class RoomOutboundMegolmSession(
    @PrimaryKey val roomId: RoomId,
    val value: String,
)

@Dao
interface OutboundMegolmSessionDao {
    @Query("SELECT * FROM OutboundMegolmSession WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: RoomId): RoomOutboundMegolmSession?

    @Query("SELECT * FROM OutboundMegolmSession")
    suspend fun getAll(): List<RoomOutboundMegolmSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomOutboundMegolmSession)

    @Query("DELETE FROM OutboundMegolmSession WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM OutboundMegolmSession")
    suspend fun deleteAll()
}

internal class RoomOutboundMegolmSessionRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : OutboundMegolmSessionRepository {
    private val dao = db.outboundMegolmSession()

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredOutboundMegolmSession> =
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: StoredOutboundMegolmSession) =
        dao.insert(
            RoomOutboundMegolmSession(
                roomId = key,
                value = json.encodeToString(value),
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) =
        dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
