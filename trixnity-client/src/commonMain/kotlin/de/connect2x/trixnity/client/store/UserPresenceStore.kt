package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.UserPresenceRepository
import de.connect2x.trixnity.core.model.UserId
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope

class UserPresenceStore(
    repository: UserPresenceRepository,
    tm: StoreTransactionManager,
    statisticCollector: ObservableCacheStatisticCollector,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val presenceCache =
        MinimalRepositoryObservableCache(
                repository = repository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = config.cacheExpireDurations.presence,
            )
            .also(statisticCollector::addCache)

    fun getPresence(userId: UserId) = presenceCache.get(userId)

    context(transaction: StoreWriteTransaction)
    suspend fun setPresence(userId: UserId, userPresence: UserPresence) = presenceCache.set(userId, userPresence)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() {}

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        presenceCache.deleteAll()
    }
}
