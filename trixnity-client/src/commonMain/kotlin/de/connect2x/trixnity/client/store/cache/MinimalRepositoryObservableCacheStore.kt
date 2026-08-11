package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.repository.MinimalRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

internal open class MinimalRepositoryObservableCacheStore<K, V>(
    private val repository: MinimalRepository<K, V>,
) : ObservableCacheStore<K, V> {
    context(transaction: ReadTransaction)
    override suspend fun get(key: K): V? = repository.get(key)

    context(transaction: WriteTransaction)
    override suspend fun persist(key: K, value: V?) =
        if (value == null) repository.delete(key)
        else repository.save(key, value)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        repository.deleteAll()
    }
}
