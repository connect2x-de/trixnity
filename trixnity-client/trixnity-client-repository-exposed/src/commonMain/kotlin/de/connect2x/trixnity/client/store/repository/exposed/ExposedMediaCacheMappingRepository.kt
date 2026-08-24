package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.MediaCacheMapping
import de.connect2x.trixnity.client.store.repository.MediaCacheMappingRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedMediaCacheMapping : Table("media_cache_mapping") {
    val cacheUri = varchar("cache_uri", length = 768)
    override val primaryKey = PrimaryKey(cacheUri)
    val mxcUri = text("mxc_uri").nullable()
    val size = long("size")
    val contentType = text("content_type").nullable()
}

internal class ExposedMediaCacheMappingRepository : MediaCacheMappingRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: String): MediaCacheMapping? {
        return ExposedMediaCacheMapping.selectAll()
            .where { ExposedMediaCacheMapping.cacheUri eq key }
            .firstOrNull()
            ?.let {
                MediaCacheMapping(
                    key,
                    it[ExposedMediaCacheMapping.mxcUri],
                    it[ExposedMediaCacheMapping.size],
                    it[ExposedMediaCacheMapping.contentType],
                )
            }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: MediaCacheMapping) {
        ExposedMediaCacheMapping.upsert {
            it[cacheUri] = key
            it[mxcUri] = value.mxcUri
            it[size] = value.size
            it[contentType] = value.contentType.toString()
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) {
        ExposedMediaCacheMapping.deleteWhere { cacheUri eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedMediaCacheMapping.deleteAll()
    }
}
