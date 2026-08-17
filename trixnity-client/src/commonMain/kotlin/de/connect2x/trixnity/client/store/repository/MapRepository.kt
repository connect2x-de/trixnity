package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

interface MapRepository<K1, K2, V> {
    fun serializeKey(firstKey: K1, secondKey: K2): String

    context(transaction: ReadTransaction)
    suspend fun get(firstKey: K1): Map<K2, V>

    context(transaction: ReadTransaction)
    suspend fun get(firstKey: K1, secondKey: K2): V?

    context(transaction: WriteTransaction)
    suspend fun save(firstKey: K1, secondKey: K2, value: V)

    context(transaction: WriteTransaction)
    suspend fun delete(firstKey: K1, secondKey: K2)

    context(transaction: WriteTransaction)
    suspend fun deleteAll()
}
