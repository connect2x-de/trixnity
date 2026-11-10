package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryRoomAccountDataStore
import de.connect2x.trixnity.client.getInMemoryRoomOutboxMessageStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.getInMemoryRoomTimelineStore
import de.connect2x.trixnity.client.getInMemoryStickyEventStore
import de.connect2x.trixnity.client.media.mappings.EventContentMediaMappings
import de.connect2x.trixnity.client.media.mappings.default
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.MediaServiceMock
import de.connect2x.trixnity.client.mocks.RoomEventEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.TimelineEventHandlerMock
import de.connect2x.trixnity.client.simpleUserInfo
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@OptIn(MSC4354::class, MSC4143::class, MSC4193::class)
class RoomServiceStickyTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val roomId1 = RoomId("1")
    private val roomId2 = RoomId("2")
    private val alice = UserId("alice", "test")
    private val bob = UserId("bob", "test")
    private val eventId1 = EventId("1")
    private val eventId2 = EventId("2")
    private val eventId3 = EventId("3")

    private val roomStore = getInMemoryRoomStore()
    private val roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore()
    private val roomTimelineStore = getInMemoryRoomTimelineStore()
    private val stickyEventStore = getInMemoryStickyEventStore { tm.writeTransaction { deleteAll() } }

    private val mediaServiceMock = MediaServiceMock()
    private val roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock()

    private val currentSyncState = MutableStateFlow(SyncState.STOPPED)
    private val api = mockMatrixClientServerApiClient()

    private val cut =
        RoomServiceImpl(
            api = api,
            roomStore = roomStore,
            roomStateStore = getInMemoryRoomStateStore(),
            roomAccountDataStore = getInMemoryRoomAccountDataStore(),
            roomTimelineStore = roomTimelineStore,
            stickyEventStore = stickyEventStore,
            roomOutboxMessageStore = roomOutboxMessageStore,
            tm = tm,
            roomEventEncryptionServices = listOf(roomEventDecryptionServiceMock),
            mediaService = mediaServiceMock,
            forgetRoomService = { _, _ -> },
            userInfo = simpleUserInfo,
            timelineEventHandler = TimelineEventHandlerMock(),
            clock = testScope.testClock,
            matrixClientConfig = MatrixClientConfiguration(),
            typingEventHandler = TypingEventHandlerImpl(api),
            currentSyncState = CurrentSyncState(currentSyncState),
            scope = testScope.backgroundScope,
            eventContentMediaMappings = EventContentMediaMappings.default,
        )

    private fun stickyTimelineEvent(roomId: RoomId, eventId: EventId, sender: UserId, content: StickyEventContent) =
        TimelineEvent(
            event =
                MessageEvent(
                    content =
                        MegolmEncryptedMessageEventContent(
                            MegolmMessageValue("ciphertext"),
                            Key.Curve25519Key(null, "senderKey").value,
                            "SENDER",
                            "SESSION",
                        ),
                    id = eventId,
                    sender = sender,
                    roomId = roomId,
                    originTimestamp = 1,
                ),
            content = Result.success(content),
        )

    private val event1 =
        stickyTimelineEvent(
            roomId = roomId1,
            eventId = eventId1,
            sender = alice,
            content =
                RtcMemberEventContent.Join(
                    slotId = RtcSlotId("slot1"),
                    member = RtcMemberEventContent.Member(RtcMemberId("sticky1")),
                    application = CallRtcApplication.Member(),
                    stickyKey = "sticky1",
                ),
        )
    private val event2 =
        stickyTimelineEvent(
            roomId = roomId1,
            eventId = eventId2,
            sender = alice,
            content =
                RtcMemberEventContent.Leave(
                    slotId = RtcSlotId("slot1"),
                    member = RtcMemberEventContent.Member(RtcMemberId("sticky1")),
                    stickyKey = "sticky1",
                ),
        )
    private val event3 =
        stickyTimelineEvent(
            roomId = roomId1,
            eventId = eventId3,
            sender = bob,
            content =
                RtcMemberEventContent.Leave(
                    slotId = RtcSlotId("slot1"),
                    member = RtcMemberEventContent.Member(RtcMemberId("sticky3")),
                    stickyKey = "sticky3",
                ),
        )

    private val event4 =
        stickyTimelineEvent(
            roomId = roomId2,
            eventId = eventId1,
            sender = alice,
            content =
                RtcMemberEventContent.Join(
                    slotId = RtcSlotId("slot1"),
                    member = RtcMemberEventContent.Member(RtcMemberId("sticky1")),
                    application = CallRtcApplication.Member(),
                    stickyKey = "sticky1",
                ),
        )

    @Test
    fun `getAllSticky - filter active`() = runTest {
        storeEvents(event1, event2, event3, event4)
        cut.getSticky<RtcMemberEventContent>(roomId1, alice, "sticky1").first() shouldBe
            event2.mergedEvent?.getOrThrow()
    }

    @Test
    fun `getAllSticky - not found with exact sender`() = runTest {
        storeEvents(event1, event2, event3, event4)
        cut.getSticky<RtcMemberEventContent>(roomId1, alice, "sticky3").first() shouldBe null
    }

    @Test
    fun `getAllSticky - no filter active`() = runTest {
        storeEvents(event1, event2, event3, event4)
        cut.getAllSticky<RtcMemberEventContent>().first() shouldBe
            mapOf(
                roomId1 to
                    listOf(
                        event1.mergedEvent?.getOrThrow(),
                        event2.mergedEvent?.getOrThrow(),
                        event3.mergedEvent?.getOrThrow(),
                    ),
                roomId2 to listOf(event4.mergedEvent?.getOrThrow()),
            )
    }

    @Test
    fun `getAllSticky - by room - no filter active`() = runTest {
        storeEvents(event1, event2, event3, event4)
        cut.getAllSticky<RtcMemberEventContent>(roomId1).first() shouldBe
            listOf(event1.mergedEvent?.getOrThrow(), event2.mergedEvent?.getOrThrow(), event3.mergedEvent?.getOrThrow())
    }

    @Test
    fun `getAllStickyMap - filter active`() = runTest {
        storeEvents(event1, event2, event3, event4)
        cut.getAllStickyMap<RtcMemberEventContent>().first() shouldBe
            mapOf(
                roomId1 to
                    mapOf(
                        (alice to "sticky1") to event2.mergedEvent?.getOrThrow(),
                        (bob to "sticky3") to event3.mergedEvent?.getOrThrow(),
                    ),
                roomId2 to mapOf((alice to "sticky1") to event4.mergedEvent?.getOrThrow()),
            )
    }

    @Test
    fun `getAllStickyMap - by room - filter active`() = runTest {
        storeEvents(event1, event2, event3, event4)
        cut.getAllStickyMap<RtcMemberEventContent>(roomId1).first() shouldBe
            mapOf(
                (alice to "sticky1") to event2.mergedEvent?.getOrThrow(),
                (bob to "sticky3") to event3.mergedEvent?.getOrThrow(),
            )
    }

    private suspend fun storeEvents(vararg timelineEvents: TimelineEvent) {
        tm.writeTransaction {
            timelineEvents.forEach { timelineEvent ->
                stickyEventStore.save(
                    StoredStickyEvent(timelineEvent.roomId, timelineEvent.eventId, Instant.fromEpochMilliseconds(24))
                )
                roomTimelineStore.add(timelineEvent)
            }
        }
    }
}
