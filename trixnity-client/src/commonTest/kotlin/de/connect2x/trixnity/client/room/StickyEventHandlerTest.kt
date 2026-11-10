package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.store.StickyEventStore
import de.connect2x.trixnity.client.store.StoredStickyEvent
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
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RedactedStickyEventContentImpl
import de.connect2x.trixnity.core.model.events.StickyEventData
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(MSC4354::class, MSC4143::class)
class StickyEventHandlerTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val roomId = RoomId("!room:server")
    private val alice = UserId("alice", "server")

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)
    private val repository = InMemoryStickyEventRepository()

    private val store =
        StickyEventStore(
                repository,
                NoOpStoreTransactionManager,
                MatrixClientConfiguration(),
                ObservableCacheStatisticCollector(),
                testScope.backgroundScope,
                testScope.testClock,
            )
            .apply {
                scheduleSetup {
                    init(backgroundScope)
                    tm.writeTransaction { deleteAll() }
                }
            }

    private val cut =
        StickyEventHandler(
            api = api,
            stickyEventStore = store,
            clock = testScope.testClock,
            tm = tm,
            config = MatrixClientConfiguration().apply { experimentalFeatures.enableMSC4354 = true },
        )

    @OptIn(MSC4193::class)
    @Test
    fun `setStickyEvents - skip when not sticky`() = runTest {
        val event =
            RoomEvent.MessageEvent(
                content =
                    RtcMemberEventContent.Join(
                        CallRtcApplication.SLOT_ID,
                        RtcMemberEventContent.Member(RtcMemberId("memberId")),
                        CallRtcApplication.Member(),
                        null,
                        "sticky",
                    ) as MessageEventContent,
                id = EventId("\$event"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 1000L,
                sticky = null,
            )
        cut.setStickyEvents(listOf(event))
        store.getAll().first().shouldBeEmpty()
    }

    @OptIn(MSC4193::class)
    @Test
    fun `setStickyEvents - skip when no remaining duration`() = runTest {
        val event =
            RoomEvent.MessageEvent(
                content =
                    RtcMemberEventContent.Join(
                        CallRtcApplication.SLOT_ID,
                        RtcMemberEventContent.Member(RtcMemberId("memberId")),
                        CallRtcApplication.Member(),
                        null,
                        "sticky",
                    ) as MessageEventContent,
                id = EventId("\$event"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 1000L,
                sticky = StickyEventData(durationMs = 10.minutes.inWholeMilliseconds),
            )
        cut.setStickyEvents(listOf(event))
        store.getAll().first().shouldBeEmpty()
    }

    @OptIn(MSC4193::class)
    @Test
    fun `setStickyEvents - skip when negative remaining duration`() = runTest {
        val event =
            RoomEvent.MessageEvent(
                content =
                    RtcMemberEventContent.Join(
                        CallRtcApplication.SLOT_ID,
                        RtcMemberEventContent.Member(RtcMemberId("memberId")),
                        CallRtcApplication.Member(),
                        null,
                        "sticky",
                    ) as MessageEventContent,
                id = EventId("\$event"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 1000L,
                sticky = StickyEventData(durationMs = 10.minutes.inWholeMilliseconds),
                unsigned =
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        stickyDurationRemainingMs = -5.minutes.inWholeMilliseconds
                    ),
            )
        cut.setStickyEvents(listOf(event))
        store.getAll().first().shouldBeEmpty()
    }

    @Test
    fun `setStickyEvents - save sticky events`() = runTest {
        val event1 =
            RoomEvent.MessageEvent(
                content =
                    RtcMemberEventContent.Leave(
                        slotId = RtcSlotId("slot"),
                        member = RtcMemberEventContent.Member(RtcMemberId("memberId")),
                        reason = null,
                        stickyKey = "sticky",
                    ) as MessageEventContent,
                id = EventId("\$event1"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 2000L,
                sticky = StickyEventData(durationMs = 10.minutes.inWholeMilliseconds),
                unsigned =
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        stickyDurationRemainingMs = 5.minutes.inWholeMilliseconds
                    ),
            )
        val event2 =
            RoomEvent.MessageEvent(
                content =
                    RtcMemberEventContent.Leave(
                        slotId = RtcSlotId("slot"),
                        member = RtcMemberEventContent.Member(RtcMemberId("memberId")),
                        reason = null,
                        stickyKey = "sticky",
                    ) as MessageEventContent,
                id = EventId("\$event2"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 2000L,
                sticky = StickyEventData(durationMs = 10.minutes.inWholeMilliseconds),
                unsigned =
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        stickyDurationRemainingMs = 7.minutes.inWholeMilliseconds
                    ),
            )
        cut.setStickyEvents(listOf(event1, event2))
        store.getAll().flattenValues(ZERO).first() shouldBe
            setOf(
                StoredStickyEvent(roomId = roomId, eventId = event1.id, endTime = testClock.now() + 5.minutes),
                StoredStickyEvent(roomId = roomId, eventId = event2.id, endTime = testClock.now() + 7.minutes),
            )
    }

    @Test
    fun `setStickyEvents - handle redacted sticky events`() = runTest {
        val redactedEvent =
            RoomEvent.MessageEvent(
                content =
                    RedactedStickyEventContentImpl("org.matrix.msc4143.rtc.member", "sticky") as MessageEventContent,
                id = EventId("\$event1"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 1000L,
                sticky = StickyEventData(durationMs = 10.minutes.inWholeMilliseconds),
                unsigned =
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        stickyDurationRemainingMs = 5.minutes.inWholeMilliseconds
                    ),
            )
        cut.setStickyEvents(listOf(redactedEvent))
        store.getAll().flattenValues(ZERO).first() shouldBe
            setOf(StoredStickyEvent(roomId = roomId, eventId = redactedEvent.id, endTime = testClock.now() + 5.minutes))
    }

    @OptIn(MSC4193::class)
    @Test
    fun `setStickyEvents - set end time based on now and remaining duration`() = runTest {
        // normal duration
        val event1 =
            RoomEvent.MessageEvent(
                content =
                    RtcMemberEventContent.Join(
                        CallRtcApplication.SLOT_ID,
                        RtcMemberEventContent.Member(RtcMemberId("memberId")),
                        CallRtcApplication.Member(),
                        null,
                        "sticky1",
                    ) as MessageEventContent,
                id = EventId("\$event1"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 2000L,
                sticky = StickyEventData(durationMs = 10.minutes.inWholeMilliseconds),
                unsigned =
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        stickyDurationRemainingMs = 5.minutes.inWholeMilliseconds
                    ),
            )
        // over 1 hour
        val event2 =
            RoomEvent.MessageEvent(
                content =
                    RtcMemberEventContent.Join(
                        CallRtcApplication.SLOT_ID,
                        RtcMemberEventContent.Member(RtcMemberId("memberId")),
                        CallRtcApplication.Member(),
                        null,
                        "sticky2",
                    ) as MessageEventContent,
                id = EventId("\$event2"),
                sender = alice,
                roomId = roomId,
                originTimestamp = 2000L,
                sticky = StickyEventData(durationMs = 2.hours.inWholeMilliseconds),
                unsigned =
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        stickyDurationRemainingMs = (1.hours + 1.seconds).inWholeMilliseconds
                    ),
            )
        delay(2.seconds)
        cut.setStickyEvents(listOf(event1, event2))

        store.getAll().flattenValues(ZERO).first() shouldBe
            setOf(
                StoredStickyEvent(roomId = roomId, eventId = event1.id, endTime = testClock.now() + 5.minutes),
                StoredStickyEvent(roomId = roomId, eventId = event2.id, endTime = testClock.now() + 1.hours),
            )
    }

    @Test
    fun `removeInvalidStickyEvents - periodically`() = runTest {
        val eventId1 = EventId("\$event1")
        val eventId2 = EventId("\$event2")
        val endTime1 = testClock.now() + 2.minutes
        val endTime2 = testClock.now() + 5.minutes
        tm.writeTransaction {
            store.save(StoredStickyEvent(roomId = roomId, eventId = eventId1, endTime = endTime1))
            store.save(StoredStickyEvent(roomId = roomId, eventId = eventId2, endTime = endTime2))
        }
        val job = backgroundScope.launch { cut.removeInvalidStickyEvents() }
        delay(1.seconds)
        store.getAll().flattenValues(ZERO).first() shouldBe
            setOf(
                StoredStickyEvent(roomId = roomId, eventId = eventId1, endTime = endTime1),
                StoredStickyEvent(roomId = roomId, eventId = eventId2, endTime = endTime2),
            )

        delay(2.minutes)
        store.getAll().flattenValues(ZERO).first() shouldBe
            setOf(StoredStickyEvent(roomId = roomId, eventId = eventId2, endTime = endTime2))

        delay(3.minutes)
        store.getAll().flattenValues(ZERO).first().shouldBeEmpty()
        job.cancel()
    }
}
