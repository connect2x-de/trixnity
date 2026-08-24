package de.connect2x.trixnity.client.store

import kotlinx.coroutines.CoroutineScope

interface Store {
    suspend fun init(coroutineScope: CoroutineScope) {}

    /** Only deletes everything, that can be fetched from server. */
    context(transaction: StoreWriteTransaction)
    suspend fun clearCache()

    /** Deletes everything. */
    context(transaction: StoreWriteTransaction)
    suspend fun deleteAll()
}
