package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

open class InMemoryObservableCacheStore<K, V> : ObservableCacheStore<K, V> {
    private var readDelay = Duration.ZERO
    private var writeDelay = Duration.ZERO

    val values = MutableStateFlow(mapOf<K, V>())

    context(transaction: ReadTransaction)
    override suspend fun get(key: K): V? {
       delay(readDelay)
        return values.value[key]
    }

    context(transaction: WriteTransaction)
    override suspend fun persist(key: K, value: V?) {
         delay(writeDelay)
        if (value == null) values.update { it - key }
        else values.update { it + (key to value) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        delay(writeDelay)
        values.value = emptyMap()
    }
}
