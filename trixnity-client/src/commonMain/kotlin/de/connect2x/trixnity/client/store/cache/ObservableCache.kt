package de.connect2x.trixnity.client.store.cache

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.store.StoreWriteTransaction
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.TransactionManager
import de.connect2x.trixnity.utils.WriteTransaction
import de.connect2x.trixnity.utils.concurrentMutableMap
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val log = Logger("de.connect2x.trixnity.client.store.cache.ObservableCache")

internal sealed interface CacheValue<T> {
    class Init<T> : CacheValue<T>

    @JvmInline value class Value<T>(val value: T) : CacheValue<T>

    fun valueOrNull() =
        when (this) {
            is Init -> null
            is Value -> value
        }
}

/** The actual source and sink of the data to be cached. This could be any database. */
internal interface ObservableCacheStore<K, V> {
    /** Retrieve value from store. */
    context(transaction: ReadTransaction)
    suspend fun get(key: K): V?

    /** Save value to store. */
    context(transaction: WriteTransaction)
    suspend fun persist(key: K, value: V?)

    /** Delete all values from store. */
    context(transaction: WriteTransaction)
    suspend fun deleteAll()
}

/** An index to track which entries have been added to or removed from the cache. */
internal interface ObservableCacheIndex<K> {
    /** Called, when an entry is added to the cache. */
    suspend fun onPut(key: K)

    /** Called, when an entry has skipped the cache. Skipping is done, when there is no subscriber of a cache entry. */
    suspend fun onSkipPut(key: K)

    /**
     * Called, when an entry is removed from the cache.
     *
     * @param stale means that the value has been deleted from the database. It is only set to true, when no-one listens
     *   to this specific key.
     */
    suspend fun onRemove(key: K, stale: Boolean)

    /** Called, when all entries are removed from the cache. */
    suspend fun onRemoveAll()

    /** Get the subscription count on an index entry, which uses an entry of the cache. */
    suspend fun getSubscriptionCount(key: K): Int

    suspend fun collectStatistic(): ObservableCacheIndexStatistic?
}

/**
 * Base class to create a coroutine and [StateFlow] based cache.
 *
 * @param name The name is just used for logging.
 * @param cacheScope A long living [CoroutineScope] to spawn coroutines, which remove entries from cache when not used
 *   anymore.
 * @param expireDuration Duration to wait until entries from cache are when not used anymore.
 */
internal open class ObservableCache<K : Any, V, S : ObservableCacheStore<K, V>>(
    val name: String,
    protected val store: S,
    private val tm: TransactionManager<*, *>,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
    private val removeFromCacheOnNull: Boolean = false,
    private val values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
) {
    private val removerIndex =
        if (expireDuration.isInfinite().not()) {
            RemoverJobExecutingIndex(name, values, clock, expireDuration).also { addIndex(it) }
        } else null

    init {
        if (removerIndex != null)
            cacheScope.launch {
                while (isActive) {
                    delay(2.seconds)
                    removerIndex.invalidateCache()
                }
            }
    }

    fun addIndex(index: ObservableCacheIndex<K>) {
        values.indexes.update { it + index }
    }

    suspend fun invalidate() {
        removerIndex?.invalidateCache()
    }

    suspend fun clear() {
        values.removeAll()
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteAll() {
        store.deleteAll()
        clear()
    }

    fun get(key: K): Flow<V?> = flow {
        val cacheEntry =
            withContext(NonCancellable) {
                values.getOrPut(key) {
                    log.trace { "$name (get): no cache hit for key $key" }
                    MutableStateFlow(CacheValue.Init())
                }
            }
        cacheEntry.get { tm.readTransaction { store.get(key) } }
        emitAll(cacheEntry.filterIsInstance<CacheValue.Value<V?>>().map { it.value })
    }

    context(transaction: StoreWriteTransaction)
    suspend fun set(key: K, value: V?, onPersist: (newValue: V?) -> Unit = {}) {
        set(key = key, value = value, persist = { store.persist(key, value).also { onPersist(value) } })
    }

    context(transaction: CacheTransaction)
    suspend fun setCacheOnly(key: K, value: V?) {
        set(key = key, value = value, persist = null)
    }

    context(transaction: CacheTransaction)
    private suspend fun set(key: K, value: V?, persist: (suspend () -> Unit)?) {
        if (values.get(key) == null && values.getIndexSubscriptionCount(key) == 0) {
            log.trace {
                "$name (set): skip cache and persist directly because there is no cache entry or subscriber for key $key"
            }
            persist?.invoke()
            values.skipPut(key)
            transaction.onCommitActions.write {
                add {
                    val cacheEntry = values.get(key)
                    if (cacheEntry != null || values.getIndexSubscriptionCount(key) > 0) {
                        log.trace {
                            "$name (set): skip cache but found a cache entry or subscriber and therefore filling it for key $key"
                        }
                        (cacheEntry ?: values.getOrPut(key) { MutableStateFlow(CacheValue.Init()) })
                            .getAndUpdate { CacheValue.Value(value) }
                            .valueOrNull()
                            .also { oldValue -> possiblyRemoveFromCache(key, oldValue, value) }
                    }
                }
            }
        } else {
            val cacheEntry =
                values.getOrPut(key) {
                    log.trace { "$name (set): no cache hit for key $key" }
                    MutableStateFlow(CacheValue.Init())
                }
            cacheEntry.set(key = key, newValue = value, persist = persist)
        }
    }

    context(transaction: StoreWriteTransaction)
    suspend fun update(key: K, onPersist: (newValue: V?) -> Unit = {}, updater: (oldValue: V?) -> V?) {
        update(
            key = key,
            get = { store.get(key) },
            persist = { newValue -> store.persist(key, newValue).also { onPersist(newValue) } },
            updater = updater,
        )
    }

    context(transaction: CacheTransaction)
    suspend fun updateCacheOnly(key: K, updater: (oldValue: V?) -> V?) {
        update(key = key, get = { tm.readTransaction { store.get(key) } }, persist = null, updater = updater)
    }

    context(transaction: CacheTransaction)
    private suspend fun update(
        key: K,
        get: suspend () -> V?,
        persist: (suspend (V?) -> Unit)?,
        updater: (oldValue: V?) -> V?,
    ) {
        val cacheEntry =
            values.getOrPut(key) {
                log.trace { "$name (update): no cache hit for key $key" }
                MutableStateFlow(CacheValue.Init())
            }
        cacheEntry.update(key = key, updater = updater, get = get, persist = persist)
    }

    private suspend inline fun <V> MutableStateFlow<CacheValue<V?>>.get(noinline get: (suspend () -> V?)) {
        while (true) {
            val oldRawValue = value
            val oldValue =
                when (oldRawValue) {
                    is CacheValue.Init -> get()
                    is CacheValue.Value -> oldRawValue.value
                }
            val newRawValue = CacheValue.Value(oldValue)
            if (compareAndSet(oldRawValue, newRawValue)) {
                return
            }
        }
    }

    context(transaction: CacheTransaction)
    private suspend inline fun MutableStateFlow<CacheValue<V?>>.set(
        key: K,
        newValue: V?,
        noinline persist: (suspend () -> Unit)? = null,
    ) {
        while (true) {
            val oldRawValue = value
            val oldValue = oldRawValue.valueOrNull()
            if (newValue != null && persist == null && oldRawValue is CacheValue.Value) {
                log.trace { "$name (set): skip cache set for key $key because it is already cached" }
                break
            }
            val newRawValue = CacheValue.Value(newValue)
            if (compareAndSetPersisting(oldRawValue, newRawValue, key, persist, oldValue, newValue)) break
        }
    }

    context(transaction: CacheTransaction)
    private suspend inline fun MutableStateFlow<CacheValue<V?>>.update(
        key: K,
        noinline updater: ((oldValue: V?) -> V?),
        noinline get: (suspend () -> V?),
        noinline persist: (suspend (newValue: V?) -> Unit)? = null,
    ) {
        while (true) {
            val oldRawValue = value
            val oldValue =
                when (oldRawValue) {
                    is CacheValue.Init -> get()
                    is CacheValue.Value -> oldRawValue.value
                }
            val newValue = updater(oldValue)
            val newRawValue = CacheValue.Value(newValue)
            if (
                compareAndSetPersisting(
                    oldRawValue = oldRawValue,
                    newRawValue = newRawValue,
                    key = key,
                    persist = persist?.run { { persist(newValue) } },
                    oldValue = oldValue,
                    newValue = newValue,
                )
            )
                break
        }
    }

    context(transaction: CacheTransaction)
    private suspend fun MutableStateFlow<CacheValue<V?>>.compareAndSetPersisting(
        oldRawValue: CacheValue<V?>,
        newRawValue: CacheValue.Value<V?>,
        key: K,
        persist: (suspend () -> Unit)?,
        oldValue: V?,
        newValue: V?,
    ): Boolean {
        if (compareAndSet(oldRawValue, newRawValue)) {
            addCacheTransactionSetActions(key, oldRawValue, newRawValue)
            when {
                persist == null -> {}
                oldValue != newValue -> persist()
                else ->
                    log.trace {
                        "$name (compareSetPersist): skip cache persist for key $key because there was no change"
                    }
            }
            return true
        }
        return false
    }

    context(transaction: CacheTransaction)
    private suspend fun MutableStateFlow<CacheValue<V?>>.addCacheTransactionSetActions(
        key: K,
        oldValue: CacheValue<V?>,
        newValue: CacheValue.Value<V?>,
    ) {
        transaction.onCommitActions.write {
            add { possiblyRemoveFromCache(key, oldValue.valueOrNull(), newValue.valueOrNull()) }
        }
        transaction.onRollbackActions.write {
            add {
                log.trace { "$name (set): rollback cache update for key $key" }
                if (compareAndSet(newValue, oldValue).not()) {
                    log.warn {
                        "$name (set): cache entry has been updated outside of this transaction. Force rollback for key $key"
                    }
                    value = oldValue
                }
            }
        }
    }

    private suspend fun possiblyRemoveFromCache(key: K, oldValue: V?, newValue: V?): Boolean =
        if (removeFromCacheOnNull && newValue == null && oldValue != null) {
            log.trace {
                "$name: remove value from cache with key $key because it is stale and is allowed to remove (will never be not-null again)"
            }
            values.remove(key, true)
            true
        } else false

    internal suspend fun collectStatistic(): ObservableCacheStatistic {
        val (all, subscribed) = values.internalRead { count() to values.count { it.subscriptionCount.value > 0 } }
        return ObservableCacheStatistic(
            name = name,
            all = all,
            subscribed = subscribed,
            indexes = values.indexes.value.mapNotNull { it.collectStatistic() },
        )
    }
}

internal class RemoverJobExecutingIndex<K : Any, V>(
    private val name: String,
    private val cacheValues: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>>,
    private val clock: Clock,
    private val expireDuration: Duration,
) : ObservableCacheIndex<K> {
    private val removeAfter = concurrentMutableMap<K, Instant>()

    suspend fun invalidateCache() {
        if (removeAfter.read { isNotEmpty() }) {
            log.trace { "$name: start invalidate cache" }
            val now = clock.now()
            val (unsubscribed, subscribed) =
                removeAfter.read {
                    val partition = entries.partition { (key, _) ->
                        val cacheValue = cacheValues.get(key)
                        (cacheValue?.subscriptionCount?.value ?: 0) == 0 &&
                            (cacheValue?.value?.valueOrNull() == null ||
                                cacheValues.getIndexSubscriptionCount(key) == 0)
                    }
                    // copy() is needed because, using Map.Entry from a mutable map is not safe to use.
                    partition.first.map { it.copy() } to partition.second.map { it.key }
                }
            coroutineScope {
                launch {
                    val nextExpiration = now + expireDuration
                    log.trace { "$name: update invalidation to $nextExpiration for ${subscribed.size} entries" }
                    removeAfter.write { putAll(subscribed.map { it to nextExpiration }) }
                }
                launch {
                    log.trace { "$name: check invalidation at $now for ${unsubscribed.size} entries" }
                    unsubscribed.forEach { (key, value) ->
                        if (now > value) {
                            val cacheValue = cacheValues.get(key)
                            if (cacheValue != null) {
                                val stale = cacheValue.value.valueOrNull() == null
                                log.trace { "$name: remove value from cache with key $key (stale=$stale)" }
                                cacheValues.remove(key, stale)
                            }
                        }
                    }
                }
            }
            log.trace { "$name: finished invalidate cache" }
        }
    }

    override suspend fun onPut(key: K) {
        removeAfter.write { put(key, clock.now() + expireDuration) }
    }

    override suspend fun onSkipPut(key: K) {}

    override suspend fun onRemove(key: K, stale: Boolean) {
        removeAfter.write { remove(key) }
    }

    override suspend fun onRemoveAll() {
        removeAfter.write { clear() }
    }

    override suspend fun collectStatistic(): ObservableCacheIndexStatistic? = null

    override suspend fun getSubscriptionCount(key: K): Int = 0
}
