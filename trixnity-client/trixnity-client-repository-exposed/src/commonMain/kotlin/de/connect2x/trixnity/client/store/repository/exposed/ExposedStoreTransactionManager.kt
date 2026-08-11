package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoreReadTransaction
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoreWriteTransaction
import de.connect2x.trixnity.client.store.cache.CacheTransaction
import de.connect2x.trixnity.client.store.repository.NoOpStoreReadTransaction
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

class ExposedStoreWriteTransaction(
    cacheTransaction: CacheTransaction,
) : StoreWriteTransaction, CacheTransaction by cacheTransaction

class ExposedStoreTransactionManager(private val database: R2dbcDatabase) : StoreTransactionManager() {
    override suspend fun <T> repositoryReadTransaction(
        block: suspend StoreReadTransaction.() -> T
    ): T =
        suspendTransaction(database, readOnly = true) {
            block(NoOpStoreReadTransaction)
        }

    override suspend fun <T> repositoryWriteTransaction(
        cacheTransaction: CacheTransaction,
        block: suspend StoreWriteTransaction.() -> T
    ): T =
        suspendTransaction(database, readOnly = false) {
            block(ExposedStoreWriteTransaction(cacheTransaction))
        }
}
