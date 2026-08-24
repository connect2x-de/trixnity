package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.AccountRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class AccountStore(
    repository: AccountRepository,
    tm: StoreTransactionManager,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val accountCache =
        MinimalRepositoryObservableCache(repository, tm, storeScope, clock, Duration.INFINITE)
            .also(statisticCollector::addCache)

    suspend fun getAccount() = accountCache.get(1).first()

    fun getAccountAsFlow() = accountCache.get(1)

    context(transaction: StoreWriteTransaction)
    suspend fun updateAccount(updater: (Account?) -> Account?) = accountCache.update(1) { account -> updater(account) }

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() {}

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        accountCache.deleteAll()
    }
}
