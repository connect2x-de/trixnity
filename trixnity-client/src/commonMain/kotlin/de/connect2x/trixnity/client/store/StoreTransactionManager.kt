package de.connect2x.trixnity.client.store

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.store.cache.CacheTransaction
import de.connect2x.trixnity.client.store.cache.withCacheTransaction
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.TransactionManager
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private val log = Logger("de.connect2x.trixnity.client.store.TransactionManager")

abstract class StoreTransactionManager : TransactionManager<StoreReadTransaction, StoreWriteTransaction> {
    protected abstract suspend fun <T> repositoryReadTransaction(block: suspend StoreReadTransaction.() -> T): T

    protected abstract suspend fun <T> repositoryWriteTransaction(
        cacheTransaction: CacheTransaction,
        block: suspend StoreWriteTransaction.() -> T,
    ): T

    override suspend fun <T> readTransaction(block: suspend StoreReadTransaction.() -> T): T =
        repositoryReadTransaction(block)

    override suspend fun <T> writeTransaction(block: suspend StoreWriteTransaction.() -> T): T =
        withContext(NonCancellable) { // prevent that the store and cache get out of sync on a CancellationException
            withCacheTransaction { repositoryWriteTransaction(this, block) }
        }
}

interface StoreReadTransaction : ReadTransaction

interface StoreWriteTransaction : StoreReadTransaction, WriteTransaction, CacheTransaction
