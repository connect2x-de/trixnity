package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.repository.GlobalAccountDataRepository
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@Entity(tableName = "GlobalAccountData", primaryKeys = ["type", "key"])
data class RoomGlobalAccountData(val type: String, val key: String, val event: String)

@Dao
interface GlobalAccountDataDao {
    @Query("SELECT * FROM GlobalAccountData WHERE type = :type")
    suspend fun getAllByType(type: String): List<RoomGlobalAccountData>

    @Query("SELECT * FROM GlobalAccountData WHERE type = :type AND key = :key LIMIT 1")
    suspend fun getByKeys(type: String, key: String): RoomGlobalAccountData?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomGlobalAccountData)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entities: List<RoomGlobalAccountData>)

    @Query("DELETE FROM GlobalAccountData WHERE type = :type") suspend fun delete(type: String)

    @Query("DELETE FROM GlobalAccountData WHERE type = :type AND key = :key")
    suspend fun delete(type: String, key: String)

    @Query("DELETE FROM GlobalAccountData") suspend fun deleteAll()
}

internal class RoomGlobalAccountDataRepository(db: TrixnityRoomDatabase, private val json: Json) :
    GlobalAccountDataRepository {
    private val dao = db.globalAccountData()

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer =
        json.serializersModule.getContextual(GlobalAccountDataEvent::class)
            ?: throw IllegalArgumentException("could not find event serializer")

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: String): Map<String, GlobalAccountDataEvent<*>> =
        dao.getAllByType(firstKey).associate { entity -> entity.key to json.decodeFromString(serializer, entity.event) }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: String, secondKey: String): GlobalAccountDataEvent<*>? =
        dao.getByKeys(firstKey, secondKey)?.let { entity -> json.decodeFromString(serializer, entity.event) }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: String, secondKey: String, value: GlobalAccountDataEvent<*>) =
        dao.insert(
            RoomGlobalAccountData(type = firstKey, key = secondKey, event = json.encodeToString(serializer, value))
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: String, secondKey: String) = dao.delete(firstKey, secondKey)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
