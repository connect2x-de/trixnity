package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.retry
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryRoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RoomOutboxMessageStoreTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val room = RoomId("!room:server")

    private val roomOutboxMessageRepository = InMemoryRoomOutboxMessageRepository() as RoomOutboxMessageRepository
    private val cut =
        RoomOutboxMessageStore(
            roomOutboxMessageRepository = roomOutboxMessageRepository,
            tm = tm,
            config =
                MatrixClientConfiguration().apply {
                    cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(50.milliseconds)
                },
            statisticCollector = ObservableCacheStatisticCollector(),
            storeScope = testScope.backgroundScope,
            clock = testScope.testClock,
        )

    @Test
    fun `init » fill cache with values from repository`() = runTest {
        val message1 = RoomOutboxMessage(room, "t1", Text(""), Clock.System.now())
        val message2 = RoomOutboxMessage(room, "t2", Text(""), Clock.System.now())
        tm.writeTransaction {
            roomOutboxMessageRepository.save(RoomOutboxMessageRepositoryKey(room, "t1"), message1)
            roomOutboxMessageRepository.save(RoomOutboxMessageRepositoryKey(room, "t2"), message2)
        }
        retry(10, 2_000.milliseconds, 30.milliseconds) {
            cut.getAll().flattenValues().first() shouldContainExactly listOf(message1, message2)
        }
    }

    @Test
    fun `handle massive save and delete`() = runTest {
        backgroundScope.launch {
            cut.getAll().flattenValues().collect { outbox ->
                tm.writeTransaction {
                    outbox.forEach { cut.update(it.roomId, it.transactionId) { it?.copy(sentAt = Clock.System.now()) } }
                }
                delay(10)
            }
        }
        backgroundScope.launch {
            cut.getAll().flattenValues().collect { outbox ->
                tm.writeTransaction { outbox.forEach { cut.update(it.roomId, it.transactionId) { null } } }
                delay(10)
            }
        }
        tm.writeTransaction {
            repeat(50) { i ->
                cut.update(room, i.toString()) { RoomOutboxMessage(room, i.toString(), Text(""), Clock.System.now()) }
            }
        }
        cut.getAll().flatten().first { it.isEmpty() } // we get a timeout if this never succeeds
    }
}
