package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

interface MinimalRepository<K, V> {
    fun serializeKey(key: K): String

    context(transaction: ReadTransaction)
    suspend fun get(key: K): V?

    context(transaction: WriteTransaction)
    suspend fun save(key: K, value: V)

    context(transaction: WriteTransaction)
    suspend fun delete(key: K)

    context(transaction: WriteTransaction)
    suspend fun deleteAll()
}
