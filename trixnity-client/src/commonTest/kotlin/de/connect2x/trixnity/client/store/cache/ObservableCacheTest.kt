package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

class ObservableCacheTest : TrixnityBaseTest() {
    private val noOpTm = NoOpStoreTransactionManager
    private val tm = NoOpStoreTransactionManager
    private val cacheStore = InMemoryObservableCacheStore<String, String>()

    private val cut = ObservableCache(
        name = "test",
        store = cacheStore,
        tm = tm,
        cacheScope = testScope.backgroundScope,
        clock = testScope.testClock
    )
    private val indexedCut by lazy {
        TestIndexedObservableCache(
            name = "",
            store = cacheStore,
            tm = tm,
            cacheScope = testScope.backgroundScope,
            clock = testScope.testClock
        )
    }


    @Test
    fun `read » read value from repository and update cache`() = runTest {
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a new value")
        }
        cut.get(key = "key").first() shouldBe "a new value"
    }

    @Test
    fun `read » prefer cache`() = runTest {
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a new value")
        }
        cut.get(key = "key").first() shouldBe "a new value"
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a changed value")
        }
        cut.get(key = "key").first() shouldBe "a new value"

    }

    @Test
    fun `read » remove from cache when not used anymore`() = runTest {
        noOpTm.writeTransaction {
            cacheStore.persist("key", "old value")
        }

        val collectingJob = backgroundScope.launch {
            cut.get(key = "key").collect()
        }

        cut.get(key = "key").first() shouldBe "old value"
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        noOpTm.writeTransaction {
            cacheStore.persist("key", "new value")
        }
        cut.get(key = "key").first() shouldBe "old value"

        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").first() shouldBe "old value"

        collectingJob.cancel()
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").first() shouldBe "new value"
    }

    @Test
    fun `read » remove from cache when cache time expired`() = runTest {
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a new value")
        }
        cut.get(key = "key").first() shouldBe "a new value"
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        noOpTm.writeTransaction {
            cacheStore.persist("key", "another value")
        }
        cut.get(key = "key").first() shouldBe "another value"
        // we check, that the value is not removed before the time expires
        noOpTm.writeTransaction {
            cacheStore.persist("key", "yet another value")
        }
        cut.get(key = "key").stateIn(backgroundScope).value shouldBe "another value"
        // and that the value is not removed from cache, when there is a scope, that uses it
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").stateIn(backgroundScope).value shouldBe "another value"
    }

    @Test
    fun `read » infinite cache enabled » never remove from cache`() = runTest {
        val cut = ObservableCache(
            name = "",
            store = cacheStore,
            tm = tm,
            cacheScope = backgroundScope,
            clock = testClock,
            expireDuration = Duration.INFINITE
        )
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a new value")
        }
        cut.get(key = "key").stateIn(backgroundScope).value shouldBe "a new value"
        noOpTm.writeTransaction {
            cacheStore.persist("key", "aanother value")
        }
        cut.get(key = "key").first() shouldBe "a new value"
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").first() shouldBe "a new value"
    }

    @Test
    fun `write » read value from repository and update cache`() = runTest {
        noOpTm.writeTransaction {
            cacheStore.persist("key", "from db")
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
            )
        }
        tm.readTransaction {
            cacheStore.get("key") shouldBe "updated value"
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value 2"
                },
            )
        }
        tm.readTransaction {
            cacheStore.get("key") shouldBe "updated value 2"
        }
    }

    @Test
    fun `write » prefer cache`() = runTest {
        noOpTm.writeTransaction {
            cacheStore.persist("key", "from db")
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
            )
        }
        tm.readTransaction {
            cacheStore.get("key") shouldBe "updated value"
        }
        noOpTm.writeTransaction {
            cacheStore.persist("key", "from db 2")
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value 2"
                },
            )
        }
        tm.readTransaction {
            cacheStore.get("key") shouldBe "updated value 2"
        }
    }

    @Test
    fun `write » not save unchanged value`() = runTest {
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { "updated value" },
            )
        }
        tm.readTransaction {
            cacheStore.get("key") shouldBe "updated value"
        }
        noOpTm.writeTransaction {
            cacheStore.persist("key", null)
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { "updated value" },
            )
        }
        tm.readTransaction {
            cacheStore.get("key") shouldBe null
        }
    }


    @Test
    fun `write » handle massive parallel manipulation of same key`() = runTest {
        withContext(Dispatchers.Default) {
            suspend fun operation(): TimedValue<Duration> {
                val database = MutableSharedFlow<String?>(replay = 3000)

                class InMemoryObservableCacheStoreWithHistory : InMemoryObservableCacheStore<String, String>() {
                    context(transaction: WriteTransaction)
                    override suspend fun persist(key: String, value: String?) {
                      database.emit(value)
                    }
                }

                val cut = ObservableCache(
                    name = "",
                    store = InMemoryObservableCacheStoreWithHistory(),
                    tm = tm,
                    cacheScope = backgroundScope,
                    clock = testClock
                )

                val result = measureTimedValue {
                    (0..99).map { i ->
                        async {
                            measureTimedValue {
                                tm.writeTransaction {
                                    cut.update(
                                        key = "key",
                                        updater = { "$i" },
                                    )
                                }
                            }.duration
                        }
                    }.awaitAll().reduce { acc, duration -> acc + duration }
                }
                database.replayCache shouldContainAll (0..99).map { it.toString() }
                return result
            }

            operation() // warmup
            val (operationsTimeSum, completeTime) = operation()

            val timePerOperation = operationsTimeSum / 100
            println("timePerOperation=$timePerOperation completeTime=$completeTime")
            timePerOperation shouldBeLessThan 20.milliseconds // TODO values high, because currently CI is slow
            completeTime shouldBeLessThan 200.milliseconds // TODO values high, because currently CI is slow
        }
    }

    @Test
    fun `write » handle massive parallel manipulation of different keys`() = runTest {
        withContext(Dispatchers.Default) {
            suspend fun operation(): TimedValue<Duration> {
                val database = MutableSharedFlow<String?>(replay = 3000)

                class InMemoryObservableCacheStoreWithHistory : InMemoryObservableCacheStore<String, String>() {
                    context(transaction: WriteTransaction)
                    override suspend fun persist(key: String, value: String?) {
                       database.emit(key)
                    }
                }

                val cut = ObservableCache(
                    name = "",
                    store = InMemoryObservableCacheStoreWithHistory(),
                    tm = tm,
                    cacheScope = backgroundScope,
                    clock = testClock
                )
                val result = measureTimedValue {
                    (0..99).map { i ->
                        this@withContext.async {
                            measureTimedValue {
                                tm.writeTransaction {
                                    cut.update(
                                        key = "$i",
                                        updater = { "value" },
                                    )
                                }
                            }.duration
                        }
                    }.awaitAll().reduce { acc, duration -> acc + duration }
                }
                database.replayCache shouldContainAll (0..99).map { it.toString() }
                return result
            }

            operation() // warmup
            val (operationsTimeSum, completeTime) = operation()

            val timePerOperation = operationsTimeSum / 100
            println("timePerOperation=$timePerOperation completeTime=$completeTime")
            timePerOperation shouldBeLessThan 80.milliseconds // TODO values high, because currently CI is slow
            completeTime shouldBeLessThan 400.milliseconds // TODO values high, because currently CI is slow
        }
    }

    @Test
    fun `write » use same internal StateFlow when initial value is null`() = runTest {
        val readFlow = cut.get(key = "key").shareIn(backgroundScope, SharingStarted.Eagerly, 3)
        readFlow.first { it == null }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { null } // this should not create a new internal StateFlow
            )
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { "newValue" }
            )
        }
        readFlow.first { it == "newValue" }
    }

    @Test
    fun `write » fill value with set while read is active`() = runTest {
        val startedCollect = MutableStateFlow(false)
        val readResult = async { cut.get("key").onEach { startedCollect.value = true }.filterNotNull().first() }
        startedCollect.first { it }
        tm.writeTransaction {
            cut.set("key", "value")
        }
        readResult.await() shouldBe "value"
    }

    @Test
    fun `write » fill value with set when cache entry present`() = runTest {
        cut.get("key").first() shouldBe null
        tm.writeTransaction {
            cut.set("key", "value")
        }
        cut.get("key").first() shouldBe "value"
        tm.readTransaction {
            cacheStore.get("key") shouldBe "value"
        }
    }

    @Test
    fun `write » fill value with update while read is active`() = runTest {
        val startedCollect = MutableStateFlow(false)
        val readResult = async { cut.get("key").onEach { startedCollect.value = true }.filterNotNull().first() }
        startedCollect.first { it }
        tm.writeTransaction {
            cut.update("key") { "value" }
        }
        readResult.await() shouldBe "value"
    }

    @Test
    fun `write » skip cache when no read active`() = runTest {
        tm.writeTransaction {
            cut.set("key", "value")
        }
        noOpTm.writeTransaction {
            cacheStore.persist("key", "otherValue")
        }
        cut.get("key").first() shouldBe "otherValue"
    }

    @Test
    fun `write » infinite cache not enabled » remove from cache when write cache time expired`() = runTest {
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { "updated value" },
            )
        }
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        noOpTm.writeTransaction {
            cacheStore.persist("key", null)
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = {
                    it shouldBe null
                    "updated value"
                },
            )
        }
    }

    @Test
    fun `write » infinite cache not enabled » reset expireDuration on use`() = runTest {
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { "updated value 1" },
            )
        }
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = {
                    it shouldBe "updated value 1"
                    "updated value 2"
                },
            )
        }
        delay(1.milliseconds)
        cut.invalidate()
        noOpTm.writeTransaction {
            cacheStore.persist("key", null)
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = {
                    it shouldBe "updated value 2"
                    "updated value"
                },
            )
        }
    }

    @Test
    fun `write » infinite cache enabled » never remove from cache`() = runTest {
        val cut = ObservableCache(
            name = "",
            store = cacheStore,
            tm = tm,
            cacheScope = backgroundScope,
            clock = testClock,
            expireDuration = Duration.INFINITE
        )
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { "updated value" },
            )
        }
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a new value")
        }
        tm.writeTransaction {
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value"
                },
            )
        }
    }

    @Test
    fun `write » removeFromCacheOnNull enabled » remove from cache when value is null set`() = runTest {
        val values = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<String?>>>()
        val cut =
            ObservableCache(
                name = "",
                store = cacheStore,
                tm = tm,
                cacheScope = backgroundScope,
                clock = testClock,
                removeFromCacheOnNull = true,
                values = values
            )
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a new value")
        }
        cut.get(key = "key").first() shouldBe "a new value"
        values.getAll().size shouldBe 1
        tm.writeTransaction {
            cut.set("key", null)
        }
        values.getAll().size shouldBe 0
    }

    @Test
    fun `write » removeFromCacheOnNull enabled » remove from cache when value is null update`() = runTest {
        val values = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<String?>>>()
        val cut =
            ObservableCache(
                name = "",
                store = cacheStore,
                tm = tm,
                cacheScope = backgroundScope,
                clock = testClock,
                removeFromCacheOnNull = true,
                values = values
            )
        noOpTm.writeTransaction {
            cacheStore.persist("key", "a new value")
        }
        cut.get(key = "key").first() shouldBe "a new value"
        values.getAll().size shouldBe 1
        tm.writeTransaction {
            cut.update("key") { null }
        }
        values.getAll().size shouldBe 0
    }

    @Test
    fun `write » removeFromCacheOnNull enabled » keep null value cached until transaction commit`() = runTest {
        val cut = ObservableCache("test", cacheStore, tm, backgroundScope, testClock, removeFromCacheOnNull = true)

        noOpTm.writeTransaction {
            cacheStore.persist("key", "old value")
        }
        cut.get("key").first() shouldBe "old value"

        val deleteStarted = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        val deleteJob = launch {
            tm.writeTransaction {
                cut.set("key", null)
                // simulate that the deletion in the database has not gone through yet
                cacheStore.persist("key", "old value")
                deleteStarted.complete(Unit)
                allowCommit.await()
                cacheStore.persist("key", null) // now the value is deleted
            }
        }

        deleteStarted.await()

        try {
            cut.get("key").first() shouldBe null
        } finally {
            // we need to clean up the job in case the assertion above fails
            allowCommit.complete(Unit)
            deleteJob.join()
        }

        cut.get("key").first() shouldBe null
    }

    @Test
    fun `update cache entry when read during transaction after transaction`() = runTest {
        launch {
            tm.writeTransaction {
                cut.set("key", "value")
                cacheStore.persist("key", null) // simulate that write is only visible after transaction
               delay(100.milliseconds)
            }
        }
        delay(50.milliseconds)
        cut.get("key").first() shouldBe null
        delay(51.milliseconds)
        cut.get("key").first() shouldBe "value"
    }

    @Test
    fun `rollback cache entry when used during transaction and failing`() = runTest {
        tm.writeTransaction {
            cut.update("key1") { "value0" }
            cut.update("key2") { null }
        }
        launch {
            shouldThrow<RuntimeException> {
                tm.writeTransaction {
                    cut.update("key1") { "value1" }
                    cut.set("key1", "value2")
                    cut.set("key2", "value3")
                    cut.update("key2") { "value4" }
                    delay(100.milliseconds)
                    throw CancellationException("sync cancelled")
                }
            }
        }
        delay(50.milliseconds)
        cut.get("key1").first() shouldBe "value2"
        cut.get("key2").first() shouldBe "value4"
        delay(51.milliseconds)
        cut.get("key1").first() shouldBe "value0"
        cut.get("key2").first() shouldBe null
    }

    @Test
    fun `rollback cache entry on error`() = runTest {
        val throwingCacheStore = object : ObservableCacheStore<String, String> {
            context(transaction: ReadTransaction)
            override suspend fun get(key: String): String? = "old"

            context(transaction: WriteTransaction)
            override suspend fun persist(key: String, value: String?) {
                throw RuntimeException("upsi")
            }

            context(transaction: WriteTransaction)
            override suspend fun deleteAll() {
            }
        }
        val cut = ObservableCache(
            name = "test",
            store = throwingCacheStore,
            tm = tm,
            cacheScope = testScope.backgroundScope,
            clock = testScope.testClock
        )
        cut.get("key").first() shouldBe "old"
        shouldThrow<RuntimeException> {
            tm.writeTransaction {
                cut.update("key") { "new" }
            }
        }.message shouldBe "upsi"
        cut.get("key").first() shouldBe "old"
    }

    @Test
    fun `not rollback cache entry on cancellation`() = runTest {
        val onPersist = MutableStateFlow(false)
        val throwingCacheStore = object : ObservableCacheStore<String, String> {
            var value: String? = "old"

            context(transaction: ReadTransaction)
            override suspend fun get(key: String): String? = value

            context(transaction: WriteTransaction)
            override suspend fun persist(key: String, value: String?) {
                onPersist.value = true
               delay(50.milliseconds)
                this.value = value
            }

            context(transaction: WriteTransaction)
            override suspend fun deleteAll() {
            }
        }
        val cut = ObservableCache(
            name = "test",
            store = throwingCacheStore,
            tm = tm,
            cacheScope = testScope.backgroundScope,
            clock = testScope.testClock
        )
        cut.get("key").first() shouldBe "old"
        val job = async {
            tm.writeTransaction {
                cut.update("key") { "new" }
            }
        }
        onPersist.first { it }
        job.cancel()
        delay(100.milliseconds)
        cut.get("key").first() shouldBe "new"
    }

    @Test
    fun `index » call onPut on cache insert`() = runTest {
        tm.writeTransaction {
            indexedCut.update("key") { "value" }
        }
        indexedCut.index.onPut.value shouldBe "key"
        indexedCut.index.onRemove.value shouldBe null
    }

    @Test
    fun `index » call onSkipPut on cache skip`() = runTest {
        tm.writeTransaction {
            indexedCut.set("key", "value")
        }
        indexedCut.index.onSkipPut.value shouldBe "key"
        indexedCut.index.onPut.value shouldBe null
        indexedCut.index.onRemove.value shouldBe null
    }

    @Test
    fun `index » call not onPut on existing cache value`() = runTest {
        tm.writeTransaction {
            indexedCut.set("key", "value")
        }
        indexedCut.index.onPut.value = null
        tm.writeTransaction {
            indexedCut.set("key", "value")
        }
        indexedCut.index.onPut.value shouldBe null
    }

    @Test
    fun `index » call onRemove on cache remove`() = runTest {
        tm.writeTransaction {
            indexedCut.update("key") { "value" }
        }
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onPut.value shouldBe "key"
        indexedCut.index.onRemove.first() shouldBe ("key" to false)
    }

    @Test
    fun `index » call onRemoveALl on clear`() = runTest {
        tm.writeTransaction {
            indexedCut.set("key", "value")
        }
        indexedCut.clear()
        indexedCut.index.onRemoveAllCalled.value shouldBe true
    }

    @Test
    fun `index » wait for index subsciptions before remove from cache`() = runTest {
        indexedCut.index.subscriptionCount = 1
        tm.writeTransaction {
            indexedCut.update("key") { "value" }
        }
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onRemove.value shouldBe null
        indexedCut.index.subscriptionCount = 0
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onRemove.first() shouldBe ("key" to false)
    }

    @Test
    fun `index » allow remove from cache when index subscriptions gt 0 but value==null`() = runTest {
        indexedCut.index.subscriptionCount = 1
        tm.writeTransaction {
            indexedCut.update("key") { null }
        }
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onRemove.first() shouldBe ("key" to true)
    }

    @Test
    fun `index » not remove from cache when alreay null on upate`() = runTest {
        val barrier = CompletableDeferred<Unit>()
        val myFlow = async { indexedCut.get("key").onStart { barrier.complete(Unit) }.drop(1).first() }
        barrier.await()
        tm.writeTransaction {
            indexedCut.set("key", null)
            indexedCut.update("key") { "my elem" }
        }
        myFlow.await() shouldBe "my elem"
    }

    @Test
    fun `write » fill value with set when cache entry not present but subscribed`() = runTest {
        indexedCut.index.subscriptionCount = 1
        tm.writeTransaction {
            indexedCut.set("key", "value")
        }
        indexedCut.get("key").first() shouldBe "value"
        tm.readTransaction {
            cacheStore.get("key") shouldBe "value"
        }
    }
}

private class TestObservableCacheIndex<T> : ObservableCacheIndex<T> {
    val onPut = MutableStateFlow<T?>(null)
    val onSkipPut = MutableStateFlow<T?>(null)
    val onRemove = MutableStateFlow<Pair<T, Boolean>?>(null)
    val onRemoveAllCalled = MutableStateFlow(false)
    var subscriptionCount = 0

    override suspend fun onPut(key: T) {
        onPut.value = key
    }

    override suspend fun onSkipPut(key: T) {
        onSkipPut.value = key
    }


    override suspend fun onRemove(key: T, stale: Boolean) {
        onRemove.value = key to stale
    }

    override suspend fun onRemoveAll() {
        onRemoveAllCalled.value = true
    }

    override suspend fun collectStatistic(): ObservableCacheIndexStatistic? = null

    override suspend fun getSubscriptionCount(key: T): Int = subscriptionCount
}

private class TestIndexedObservableCache(
    name: String,
    store: InMemoryObservableCacheStore<String, String>,
    tm: StoreTransactionManager,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
) : ObservableCache<String, String, InMemoryObservableCacheStore<String, String>>(
    name, store, tm, cacheScope, clock, expireDuration
) {
    val index = TestObservableCacheIndex<String>()

    init {
        addIndex(index)
    }
}
