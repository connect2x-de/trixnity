package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.repository.MapRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

internal class MapRepositoryObservableCacheStore<K1, K2, V>(private val repository: MapRepository<K1, K2, V>) :
    ObservableCacheStore<MapRepositoryCoroutinesCacheKey<K1, K2>, V> {
    context(transaction: ReadTransaction)
    override suspend fun get(key: MapRepositoryCoroutinesCacheKey<K1, K2>): V? =
        repository.get(key.firstKey, key.secondKey)

    context(transaction: ReadTransaction)
    suspend fun getByFirstKey(key: K1): Map<K2, V> = repository.get(key)

    context(transaction: WriteTransaction)
    override suspend fun persist(key: MapRepositoryCoroutinesCacheKey<K1, K2>, value: V?) =
        if (value == null) repository.delete(key.firstKey, key.secondKey)
        else repository.save(key.firstKey, key.secondKey, value)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        repository.deleteAll()
    }
}
