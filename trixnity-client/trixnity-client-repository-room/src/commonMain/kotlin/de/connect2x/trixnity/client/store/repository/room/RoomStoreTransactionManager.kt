package de.connect2x.trixnity.client.store.repository.room

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import de.connect2x.trixnity.client.store.StoreReadTransaction
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoreWriteTransaction
import de.connect2x.trixnity.client.store.cache.CacheTransaction
import de.connect2x.trixnity.client.store.repository.NoOpStoreReadTransaction

class RoomStoreWriteTransaction(cacheTransaction: CacheTransaction) :
    StoreWriteTransaction, CacheTransaction by cacheTransaction

class RoomStoreTransactionManager(private val db: TrixnityRoomDatabase) : StoreTransactionManager() {
    override suspend fun <T> repositoryReadTransaction(block: suspend StoreReadTransaction.() -> T): T =
        db.useReaderConnection { transactor ->
            transactor.deferredTransaction { block(NoOpStoreReadTransaction) }
        }

    override suspend fun <T> repositoryWriteTransaction(
        cacheTransaction: CacheTransaction,
        block: suspend StoreWriteTransaction.() -> T,
    ): T = db.useWriterConnection { transactor ->
        transactor.immediateTransaction { block(RoomStoreWriteTransaction(cacheTransaction)) }
    }
}
