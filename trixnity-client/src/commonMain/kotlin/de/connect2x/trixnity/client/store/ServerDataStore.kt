package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.ServerDataRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class ServerDataStore(
    repository: ServerDataRepository,
    tm: StoreTransactionManager,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val serverDataCache =
        MinimalRepositoryObservableCache(repository, tm, storeScope, clock, Duration.INFINITE)
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    suspend fun setServerData(serverData: ServerData) = serverDataCache.set(1, serverData)

    fun getServerDataFlow() = serverDataCache.get(1).filterNotNull()

    suspend fun getServerData() = getServerDataFlow().first()

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() {}

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        serverDataCache.deleteAll()
    }
}
