package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryStickyEventRepository
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.StickyEventData
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.yield

@OptIn(MSC4354::class, MSC4143::class, MSC4193::class)
class StickyEventStoreTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val repo = InMemoryStickyEventRepository()
    private val cut =
        StickyEventStore(
            stickyEventRepository = repo,
            tm = tm,
            config = MatrixClientConfiguration(),
            statisticCollector = ObservableCacheStatisticCollector(),
            storeScope = testScope.backgroundScope,
            clock = testScope.testClock,
        )

    private val roomId = RoomId("!room:server")
    private val eventId = EventId("\$event")
    private val sender = UserId("alice", "server")
    private val event =
        RoomEvent.MessageEvent(
            content =
                RtcMemberEventContent.Join(
                    CallRtcApplication.SLOT_ID,
                    RtcMemberEventContent.Member(RtcMemberId("memberId")),
                    CallRtcApplication.Member(),
                    null,
                    "sticky_key",
                ) as StickyEventContent,
            id = eventId,
            sender = sender,
            roomId = roomId,
            originTimestamp = 2000L,
            sticky = StickyEventData(durationMs = 1L),
        )
    private val storedStickyEvent =
        StoredStickyEvent(roomId = roomId, eventId = eventId, endTime = Instant.fromEpochMilliseconds(2))

    @Test
    fun `deleteByEventId - should delete`() = runTest {
        tm.writeTransaction {
            repo.save(roomId, eventId, storedStickyEvent)
            cut.deleteByEventId(roomId, eventId)
            repo.get(roomId, eventId) shouldBe null
        }
    }

    @Test
    fun `save - should save`() = runTest {
        tm.writeTransaction {
            cut.save(storedStickyEvent)
            repo.get(roomId, eventId) shouldBe storedStickyEvent
        }
    }

    @Test
    fun `deleteInvalid - should delete when invalid`() = runTest {
        tm.writeTransaction { repo.save(roomId, eventId, storedStickyEvent) }
        delay(3.milliseconds)
        tm.writeTransaction {
            cut.deleteInvalid()
            repo.get(roomId, eventId) shouldBe null
        }
    }

    @Test
    fun `get - should return null when not valid`() = runTest {
        tm.writeTransaction { repo.save(roomId, eventId, storedStickyEvent) }
        delay(3.milliseconds)
        cut.get(roomId, eventId).first() shouldBe null
        cut.get(roomId).flattenValues(ZERO).first() shouldBe emptyList()
        cut.getAll().flattenValues(ZERO).first() shouldBe emptyList()
    }

    @Test
    fun `get - should return null when not valid anymore`() = runTest {
        tm.writeTransaction { repo.save(roomId, eventId, storedStickyEvent) }
        val result1 = backgroundScope.async {
            cut.get(roomId, eventId)
                .transform {
                    yield()
                    emit(it)
                }
                .take(2)
                .toList()
        }
        val result2 = backgroundScope.async {
            cut.get(roomId)
                .flattenValues(ZERO)
                .transform {
                    yield()
                    emit(it)
                }
                .take(2)
                .toList()
        }
        val result3 = backgroundScope.async {
            cut.getAll()
                .flattenValues(ZERO)
                .transform {
                    yield()
                    emit(it)
                }
                .take(2)
                .toList()
        }
        delay(3.milliseconds)
        result1.await() shouldBe listOf(storedStickyEvent, null)
        result2.await() shouldBe listOf(listOf(storedStickyEvent), listOf())
        result3.await() shouldBe listOf(listOf(storedStickyEvent), listOf())
    }
}
