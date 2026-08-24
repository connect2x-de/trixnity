package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.repository.MinimalRepository
import de.connect2x.trixnity.utils.TransactionManager
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal open class MinimalRepositoryObservableCache<K : Any, V>(
    repository: MinimalRepository<K, V>,
    tm: TransactionManager<*, *>,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
) :
    ObservableCache<K, V, ObservableCacheStore<K, V>>(
        name = repository::class.simpleName ?: repository::class.toString(),
        store = MinimalRepositoryObservableCacheStore(repository),
        tm = tm,
        cacheScope = cacheScope,
        clock = clock,
        expireDuration = expireDuration,
        values = values,
    )
