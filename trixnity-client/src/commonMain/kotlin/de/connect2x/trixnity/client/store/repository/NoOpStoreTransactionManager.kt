package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoreReadTransaction
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoreWriteTransaction
import de.connect2x.trixnity.client.store.cache.CacheTransaction

object NoOpStoreReadTransaction : StoreReadTransaction
class NoOpStoreWriteTransaction(cacheTransaction: CacheTransaction) : StoreWriteTransaction,
    CacheTransaction by cacheTransaction

object NoOpStoreTransactionManager : StoreTransactionManager() {
    override suspend fun <T> repositoryReadTransaction(block: suspend StoreReadTransaction.() -> T): T =
        block(NoOpStoreReadTransaction)

    override suspend fun <T> repositoryWriteTransaction(
        cacheTransaction: CacheTransaction,
        block: suspend StoreWriteTransaction.() -> T
    ): T = block(NoOpStoreWriteTransaction(cacheTransaction))

}
