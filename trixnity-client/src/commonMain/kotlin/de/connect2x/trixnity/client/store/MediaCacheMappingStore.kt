package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.MediaCacheMappingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class MediaCacheMappingStore(
    mediaCacheMappingRepository: MediaCacheMappingRepository,
    tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        uploadMediaCache.deleteAll()
    }

    private val uploadMediaCache = MinimalRepositoryObservableCache(
        mediaCacheMappingRepository,
        tm,
        storeScope,
        clock,
        config.cacheExpireDurations.mediaCacheMapping
    ).also(statisticCollector::addCache)

    suspend fun getMediaCacheMapping(cacheUri: String): MediaCacheMapping? =
        uploadMediaCache.get(cacheUri).first()

    context(transaction: StoreWriteTransaction)
    suspend fun updateMediaCacheMapping(
        cacheUri: String,
        updater: (oldMediaCacheMapping: MediaCacheMapping?) -> MediaCacheMapping?
    ) = uploadMediaCache.update(cacheUri, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun saveMediaCacheMapping(
        cacheUri: String,
        mediaCacheMapping: MediaCacheMapping
    ) = uploadMediaCache.set(cacheUri, mediaCacheMapping)

    context(transaction: StoreWriteTransaction)
    suspend fun deleteMediaCacheMapping(cacheUri: String) = uploadMediaCache.set(cacheUri, null)
}
