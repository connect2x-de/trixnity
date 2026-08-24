package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.StoreReadTransaction
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoreWriteTransaction
import de.connect2x.trixnity.client.store.repository.InMemoryMinimalRepository
import de.connect2x.trixnity.client.store.repository.NoOpStoreReadTransaction
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.client.store.repository.NoOpStoreWriteTransaction
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.utils.ReadTransaction
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MinimalRepositoryObservableCacheTest : TrixnityBaseTest() {
    private val noOpTm = NoOpStoreTransactionManager
    val readTransactionWasCalled = MutableStateFlow(false)
    val writeTransactionWasCalled = MutableStateFlow(false)

    private class TestInMemoryMapRepository : InMemoryMinimalRepository<String, String>() {
        val continueGetFirstKey = MutableStateFlow(true)

        override fun serializeKey(key: String): String = key

        context(transaction: ReadTransaction)
        override suspend fun get(key: String): String? {
            val result = super.get(key)
            continueGetFirstKey.first { it }
            return result
        }
    }

    private val repository =
        TestInMemoryMapRepository().also { scheduleSetup { noOpTm.writeTransaction { it.deleteAll() } } }
    private val tm =
        object : StoreTransactionManager() {
            override suspend fun <T> repositoryReadTransaction(block: suspend StoreReadTransaction.() -> T): T {
                return block(NoOpStoreReadTransaction).also { readTransactionWasCalled.value = true }
            }

            override suspend fun <T> repositoryWriteTransaction(
                cacheTransaction: CacheTransaction,
                block: suspend StoreWriteTransaction.() -> T,
            ): T {
                return block(NoOpStoreWriteTransaction(cacheTransaction)).also {
                    writeTransactionWasCalled.value = true
                }
            }
        }

    @BeforeTest
    fun setup() {
        readTransactionWasCalled.value = false
        writeTransactionWasCalled.value = false
    }

    private val cut = MinimalRepositoryObservableCache(repository, tm, testScope.backgroundScope, testScope.testClock)

    @Test
    fun `get » read from database`() = runTest {
        tm.writeTransaction { repository.save("key", "value") }
        cut.get("key").first() shouldBe "value"
        readTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `get » prefer cache`() = runTest {
        tm.writeTransaction { repository.save("key", "value") }
        cut.get("key").first() shouldBe "value"
        tm.writeTransaction { repository.save("key", "value2") }
        cut.get("key").first() shouldBe "value"
        readTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `save » save into database without reading old null value`() = runTest {
        tm.writeTransaction {
            cut.set("key", "value1")
            cut.set("key", "value2")
        }
        readTransactionWasCalled.value shouldBe false
        writeTransactionWasCalled.value shouldBe true
        noOpTm.readTransaction { repository.get("key") shouldBe "value2" }
    }

    @Test
    fun `save » save into database without reading old value`() = runTest {
        noOpTm.writeTransaction { repository.save("key", "value1") }
        tm.writeTransaction {
            cut.set("key", "value2")
            cut.set("key", "value3")
        }
        readTransactionWasCalled.value shouldBe false
        writeTransactionWasCalled.value shouldBe true
        noOpTm.readTransaction { repository.get("key") shouldBe "value3" }
    }

    @Test
    fun `update » read from database`() = runTest {
        noOpTm.writeTransaction { repository.save("key", "old") }
        tm.writeTransaction {
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
        }
        readTransactionWasCalled.value shouldBe false
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » prefer cache`() = runTest {
        noOpTm.writeTransaction { repository.save("key", "old") }
        tm.writeTransaction {
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
        }
        noOpTm.writeTransaction { repository.save("key", "dino") }
        tm.writeTransaction {
            cut.update("key") {
                it shouldBe "value"
                "new value"
            }
        }
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » save to database`() = runTest {
        noOpTm.writeTransaction { repository.save("key", "old") }
        tm.writeTransaction { cut.update("key") { "value" } }
        noOpTm.readTransaction { repository.get("key") shouldBe "value" }
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » allow multiple writes`() = runTest {
        repository.continueGetFirstKey.value = false
        noOpTm.writeTransaction { repository.save("key", "old") }
        val job1 = launch { tm.writeTransaction { cut.update("key") { "value1" } } }
        val job2 = launch { tm.writeTransaction { cut.update("key") { "value2" } } }
        delay(100.milliseconds)
        repository.continueGetFirstKey.value = true
        job1.join()
        job2.join()
        noOpTm.readTransaction { repository.get("key") shouldBeOneOf listOf("value1", "value2") }
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » remove from database`() = runTest {
        noOpTm.writeTransaction { repository.save("key", "old") }
        tm.writeTransaction { cut.update("key") { null } }
        noOpTm.readTransaction { repository.get("key") shouldBe null }
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » not save to repository when cache only`() = runTest {
        noOpTm.writeTransaction { repository.save("key", "old") }
        withCacheTransaction { cut.updateCacheOnly("key") { "value" } }
        noOpTm.readTransaction { repository.get("key") shouldBe "old" }
    }
}
