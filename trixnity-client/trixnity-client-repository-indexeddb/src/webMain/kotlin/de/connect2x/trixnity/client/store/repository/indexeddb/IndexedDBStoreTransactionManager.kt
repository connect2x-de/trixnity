package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.store.StoreReadTransaction
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoreWriteTransaction
import de.connect2x.trixnity.client.store.cache.CacheTransaction
import de.connect2x.trixnity.idb.utils.WrappedObjectStore
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import de.connect2x.trixnity.idb.utils.readTransaction
import de.connect2x.trixnity.idb.utils.writeTransaction
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import web.idb.IDBDatabase

class IndexedDBStoreReadTransaction(val database: IDBDatabase) : StoreReadTransaction

class IndexedDBStoreWriteTransaction(
    cacheTransaction: CacheTransaction,
    val database: IDBDatabase,
    val wrappedTransaction: WrappedTransaction,
) : StoreWriteTransaction, CacheTransaction by cacheTransaction

context(transaction: ReadTransaction)
suspend fun <T> IndexedDBRepository.withRead(
    block: suspend context(StoreReadTransaction) WrappedTransaction.(WrappedObjectStore) -> T
): T {
    return when (transaction) {
        is IndexedDBStoreReadTransaction ->
            transaction.database.readTransaction(objectStoreName) {
                block(objectStore(objectStoreName))
            }

        is IndexedDBStoreWriteTransaction ->
            with(transaction.wrappedTransaction) {
                block(objectStore(objectStoreName))
            }

        else -> throw IllegalStateException("required IndexedDBReadTransaction but got ${transaction::class}")
    }
}

context(transaction: WriteTransaction)
suspend fun <T> IndexedDBRepository.withWrite(
    block: suspend context(IndexedDBStoreWriteTransaction) WrappedTransaction.(WrappedObjectStore) -> T
) {
    require(transaction is IndexedDBStoreWriteTransaction) { "required IndexedDBStoreWriteTransaction but got ${transaction::class}" }
    return with(transaction.wrappedTransaction) {
        block(objectStore(objectStoreName))
    }
}

class IndexedDBStoreTransactionManager(
    private val database: IDBDatabase,
    private val allObjectStores: Array<String>,
) : StoreTransactionManager() {
    override suspend fun <T> repositoryReadTransaction(
        block: suspend StoreReadTransaction.() -> T
    ): T =
        // we do not actually create a read transaction, because each operation creates its own for performance reasons
        block(IndexedDBStoreReadTransaction(database))

    override suspend fun <T> repositoryWriteTransaction(
        cacheTransaction: CacheTransaction,
        block: suspend StoreWriteTransaction.() -> T
    ): T =
        database.writeTransaction(*allObjectStores) {
            block(IndexedDBStoreWriteTransaction(cacheTransaction, database, this))
        }
}
