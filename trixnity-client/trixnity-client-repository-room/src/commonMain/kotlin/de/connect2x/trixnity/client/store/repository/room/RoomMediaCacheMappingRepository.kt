package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.MediaCacheMapping
import de.connect2x.trixnity.client.store.repository.MediaCacheMappingRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

@Entity(tableName = "MediaCacheMapping")
data class RoomMediaCacheMapping(
    @PrimaryKey val cacheUri: String,
    val mxcUri: String?,
    val size: Long,
    val contentType: String?,
)

@Dao
interface MediaCacheMappingDao {
    @Query("SELECT * FROM MediaCacheMapping WHERE cacheUri = :cacheUri LIMIT 1")
    suspend fun get(cacheUri: String): RoomMediaCacheMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomMediaCacheMapping)

    @Query("DELETE FROM MediaCacheMapping WHERE cacheUri = :cacheUri")
    suspend fun delete(cacheUri: String)

    @Query("DELETE FROM MediaCacheMapping")
    suspend fun deleteAll()
}

internal class RoomMediaCacheMappingRepository(
    db: TrixnityRoomDatabase,
) : MediaCacheMappingRepository {
    private val dao = db.mediaCacheMapping()

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): MediaCacheMapping? =
        dao.get(key)?.let { entity ->
            MediaCacheMapping(
                cacheUri = entity.cacheUri,
                mxcUri = entity.mxcUri,
                size = entity.size,
                contentType = entity.contentType,
            )
        }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: MediaCacheMapping) =
        dao.insert(
            RoomMediaCacheMapping(
                cacheUri = value.cacheUri,
                mxcUri = value.mxcUri,
                size = value.size,
                contentType = value.contentType,
            )
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) =
        dao.delete(cacheUri = key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() =
        dao.deleteAll()
}
