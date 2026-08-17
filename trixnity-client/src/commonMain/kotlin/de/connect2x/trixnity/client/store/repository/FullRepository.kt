package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.utils.ReadTransaction

interface FullRepository<K, V> : MinimalRepository<K, V> {
    context(transaction: ReadTransaction)
    suspend fun getAll(): List<V>
}
