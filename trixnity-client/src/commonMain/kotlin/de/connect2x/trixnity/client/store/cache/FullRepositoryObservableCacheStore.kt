package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.repository.FullRepository
import de.connect2x.trixnity.utils.ReadTransaction

internal class FullRepositoryObservableCacheStore<K, V>(private val repository: FullRepository<K, V>) :
    MinimalRepositoryObservableCacheStore<K, V>(repository) {
    context(transaction: ReadTransaction)
    suspend fun getAll() = repository.getAll()
}
