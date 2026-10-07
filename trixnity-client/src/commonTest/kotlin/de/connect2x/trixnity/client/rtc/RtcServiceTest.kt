package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryStickyEventStore
import de.connect2x.trixnity.client.key.KeyService
import de.connect2x.trixnity.client.mocks.KeyServiceMock
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.StickyEventData
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.key.EventTrustLevel
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject

@OptIn(MSC4143::class, MSC4354::class, MSC4193::class)
class RtcServiceTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager

    private val room = RoomId("!room:server")
    private val alice = UserId("@alice:server")
    private val bob = UserId("@bob:server")

    private val slotId1 = RtcSlotId("m.call", "1")
    private val slotId2 = RtcSlotId("m.call", "2")
    private val slotId3 = RtcSlotId("m.call", "3")

    private val memberId1 = RtcMemberId("member1")
    private val memberId2 = RtcMemberId("member2")

    private val roomStateStore = getInMemoryRoomStateStore()
    private val stickyEventStore = getInMemoryStickyEventStore()

    private val eventTrustLevels = mutableMapOf<Pair<RoomId, EventId>, EventTrustLevel>()

    private val keyService =
        object : KeyService by KeyServiceMock() {
            override fun getTrustLevel(roomId: RoomId, eventId: EventId): Flow<EventTrustLevel> =
                flowOf(eventTrustLevels[roomId to eventId] ?: EventTrustLevel.Unknown)
        }

    private val cut =
        RtcServiceImpl(roomStateStore = roomStateStore, stickyEventStore = stickyEventStore, keyService = keyService)

    @Test
    fun `getSlot » return open slot`() = runTest {
        val event = rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot))

        saveStateEvent(event)

        cut.getSlot(room, slotId1).first() shouldBe event
    }

    @Test
    fun `getSlot » return closed slot`() = runTest {
        val event = rtcSlotEvent(slotId1, RtcSlotEventContent.Closed())

        saveStateEvent(event)

        cut.getSlot(room, slotId1).first() shouldBe event
    }

    @Test
    fun `getSlot » encrypted room » only return slot with encryption property`() = runTest {
        saveEncryptionEvent()

        val unencryptedSlot =
            rtcSlotEvent(slotId1, RtcSlotEventContent.Open(application = CallRtcApplication.Slot, encryption = null))
        saveStateEvent(unencryptedSlot)

        cut.getSlot(room, slotId1).first() shouldBe null

        val encryptedSlot =
            rtcSlotEvent(
                slotId1,
                RtcSlotEventContent.Open(
                    application = CallRtcApplication.Slot,
                    encryption = RtcEncryption.Unknown("test", JsonObject(emptyMap())),
                ),
            )
        saveStateEvent(encryptedSlot)

        cut.getSlot(room, slotId1).first() shouldBe encryptedSlot
    }

    @Test
    fun `getSlot » unencrypted room » only return slot without encryption property`() = runTest {
        val encryptedSlot =
            rtcSlotEvent(
                slotId1,
                RtcSlotEventContent.Open(
                    application = CallRtcApplication.Slot,
                    encryption = RtcEncryption.Unknown("test", JsonObject(emptyMap())),
                ),
            )
        saveStateEvent(encryptedSlot)

        cut.getSlot(room, slotId1).first() shouldBe null

        val unencryptedSlot =
            rtcSlotEvent(slotId1, RtcSlotEventContent.Open(application = CallRtcApplication.Slot, encryption = null))
        saveStateEvent(unencryptedSlot)

        cut.getSlot(room, slotId1).first() shouldBe unencryptedSlot
    }

    @Test
    fun `getSlot » closed slot is returned independent of room encryption`() = runTest {
        saveEncryptionEvent()

        val event = rtcSlotEvent(slotId1, RtcSlotEventContent.Closed())

        saveStateEvent(event)

        cut.getSlot(room, slotId1).first() shouldBe event
    }

    @Test
    fun `getAllSlots » return all slots keyed by slot id`() = runTest {
        val slot1 = rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot))
        val slot2 = rtcSlotEvent(slotId2, RtcSlotEventContent.Closed())

        saveStateEvent(slot1)
        saveStateEvent(slot2)

        cut.getAllSlots(room).first() shouldBe mapOf(slotId1 to slot1, slotId2 to slot2)
    }

    @Test
    fun `getAllSlots » encrypted room » filter slots without encryption property`() = runTest {
        saveEncryptionEvent()

        val encryptedSlot =
            rtcSlotEvent(
                slotId1,
                RtcSlotEventContent.Open(
                    application = CallRtcApplication.Slot,
                    encryption = RtcEncryption.Unknown("test", JsonObject(emptyMap())),
                ),
            )
        val unencryptedSlot =
            rtcSlotEvent(slotId2, RtcSlotEventContent.Open(application = CallRtcApplication.Slot, encryption = null))
        val closedSlot = rtcSlotEvent(slotId3, RtcSlotEventContent.Closed())

        saveStateEvent(encryptedSlot)
        saveStateEvent(unencryptedSlot)
        saveStateEvent(closedSlot)

        cut.getAllSlots(room).first() shouldBe mapOf(slotId1 to encryptedSlot, slotId3 to closedSlot)
    }

    @Test
    fun `getAllSlots » unencrypted room » filter slots with encryption property`() = runTest {
        val encryptedSlot =
            rtcSlotEvent(
                slotId1,
                RtcSlotEventContent.Open(
                    application = CallRtcApplication.Slot,
                    encryption = RtcEncryption.Unknown("test", JsonObject(emptyMap())),
                ),
            )
        val unencryptedSlot =
            rtcSlotEvent(slotId2, RtcSlotEventContent.Open(application = CallRtcApplication.Slot, encryption = null))
        val closedSlot = rtcSlotEvent(slotId3, RtcSlotEventContent.Closed())

        saveStateEvent(encryptedSlot)
        saveStateEvent(unencryptedSlot)
        saveStateEvent(closedSlot)

        cut.getAllSlots(room).first() shouldBe mapOf(slotId2 to unencryptedSlot, slotId3 to closedSlot)
    }

    @Test
    fun `getMembers » closed slot » return empty map`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Closed()))
        saveRtcMemberEvent(rtcMemberEvent(alice, memberId1, slotId1))

        cut.getMembers(room, slotId1).first() shouldBe mapOf()
    }

    @Test
    fun `getMembers » return some members`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot)))

        val member1 = rtcMemberEvent(alice, memberId1, slotId1)
        val member2 = rtcMemberEvent(bob, memberId2, slotId1)

        saveRtcMemberEvent(member1)
        saveRtcMemberEvent(member2)
        eventTrustLevels[room to member1.id] = EventTrustLevel.Unauthenticated
        eventTrustLevels[room to member2.id] = EventTrustLevel.Creator(DeviceTrustLevel.CrossSigned(false))

        cut.getMembers(room, slotId1).first() shouldBe mapOf((alice to memberId1) to member1)
    }

    @Test
    fun `getMembers » ignore member from another slot`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot)))

        val member1 = rtcMemberEvent(alice, memberId1, slotId1)
        val member2 = rtcMemberEvent(bob, memberId2, slotId2)

        saveRtcMemberEvent(member1)
        saveRtcMemberEvent(member2)
        eventTrustLevels[room to member1.id] = EventTrustLevel.Unauthenticated
        eventTrustLevels[room to member2.id] = EventTrustLevel.Unauthenticated

        cut.getMembers(room, slotId1).first() shouldBe mapOf((alice to memberId1) to member1)
    }

    @Test
    fun `getMembers » ignore member from another application`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot)))

        val member1 = rtcMemberEvent(alice, memberId1, slotId1)
        val member2 =
            rtcMemberEvent(
                bob,
                memberId2,
                slotId1,
                application = RtcApplicationMember.Unknown("other", JsonObject(emptyMap())),
            )

        saveRtcMemberEvent(member1)
        saveRtcMemberEvent(member2)
        eventTrustLevels[room to member1.id] = EventTrustLevel.Unauthenticated
        eventTrustLevels[room to member2.id] = EventTrustLevel.Unauthenticated

        cut.getMembers(room, slotId1).first() shouldBe mapOf((alice to memberId1) to member1)
    }

    @Test
    fun `getMembers » ignore member with non-matching member id`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot)))

        val member = rtcMemberEvent(alice, memberId1, slotId1, memberId2.value)
        saveRtcMemberEvent(member)
        eventTrustLevels[room to member.id] = EventTrustLevel.Unauthenticated

        cut.getMembers(room, slotId1).first() shouldBe mapOf()
    }

    @Test
    fun `getMembers » ignore member whose sender is not joined`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot)))

        val member = rtcMemberEvent(alice, memberId1, slotId1)
        saveRtcMemberEvent(member)
        eventTrustLevels[room to member.id] = EventTrustLevel.Unauthenticated

        saveStateEvent(
            StateEvent(
                content = MemberEventContent(membership = Membership.LEAVE),
                id = EventId("\$member"),
                sender = alice,
                roomId = room,
                originTimestamp = 1,
                stateKey = alice.full,
            )
        )

        cut.getMembers(room, slotId1).first() shouldBe emptyMap()
    }

    @Test
    fun `getMembers » encrypted room » accept authenticated trust levels`() = runTest {
        saveEncryptionEvent()
        saveStateEvent(
            rtcSlotEvent(
                slotId1,
                RtcSlotEventContent.Open(
                    application = CallRtcApplication.Slot,
                    encryption = RtcEncryption.Unknown("test", JsonObject(emptyMap())),
                ),
            )
        )

        val trustLevels =
            listOf(
                EventTrustLevel.Creator(DeviceTrustLevel.Valid(false)),
                EventTrustLevel.UnauthenticatedBackup(DeviceTrustLevel.Valid(false)),
                EventTrustLevel.Shared(
                    senderTrustLevel = DeviceTrustLevel.Valid(false),
                    sharingTrustLevels = emptyMap(),
                ),
            )

        for (trustLevel in trustLevels) {
            val memberId = RtcMemberId(trustLevels.indexOf(trustLevel).toString())
            val member = rtcMemberEvent(alice, memberId, slotId1)
            saveRtcMemberEvent(member)
            eventTrustLevels[room to member.id] = trustLevel

            cut.getMembers(room, slotId1).first()[alice to memberId] shouldBe member
        }
    }

    @Test
    fun `getMembers » encrypted room » ignore unauthenticated and unknown trust levels`() = runTest {
        saveEncryptionEvent()
        saveStateEvent(
            rtcSlotEvent(
                slotId1,
                RtcSlotEventContent.Open(
                    application = CallRtcApplication.Slot,
                    encryption = RtcEncryption.Unknown("test", JsonObject(emptyMap())),
                ),
            )
        )

        val trustLevels = listOf(EventTrustLevel.Unauthenticated, EventTrustLevel.Unknown)

        for (trustLevel in trustLevels) {
            val memberId = RtcMemberId(trustLevels.indexOf(trustLevel).toString())
            val member = rtcMemberEvent(alice, memberId, slotId1)
            saveRtcMemberEvent(member)
            eventTrustLevels[room to member.id] = trustLevel

            cut.getMembers(room, slotId1).first()[alice to memberId] shouldBe null
        }
    }

    @Test
    fun `getMembers » unencrypted room » accept unauthenticated trust level`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot)))

        val trustLevels = listOf(EventTrustLevel.Unauthenticated)

        for (trustLevel in trustLevels) {
            val memberId = RtcMemberId(trustLevels.indexOf(trustLevel).toString())
            val member = rtcMemberEvent(alice, memberId, slotId1)
            saveRtcMemberEvent(member)
            eventTrustLevels[room to member.id] = trustLevel

            cut.getMembers(room, slotId1).first()[alice to memberId] shouldBe member
        }
    }

    @Test
    fun `getMembers » unencrypted room » reject authenticated and unknown trust levels`() = runTest {
        saveStateEvent(rtcSlotEvent(slotId1, RtcSlotEventContent.Open(CallRtcApplication.Slot)))

        val trustLevels =
            listOf(
                EventTrustLevel.Creator(DeviceTrustLevel.Valid(false)),
                EventTrustLevel.UnauthenticatedBackup(DeviceTrustLevel.Valid(false)),
                EventTrustLevel.Shared(
                    senderTrustLevel = DeviceTrustLevel.Valid(false),
                    sharingTrustLevels = emptyMap(),
                ),
            )

        for (trustLevel in trustLevels) {
            val memberId = RtcMemberId(trustLevels.indexOf(trustLevel).toString())
            val member = rtcMemberEvent(alice, memberId, slotId1)
            saveRtcMemberEvent(member)
            eventTrustLevels[room to member.id] = trustLevel

            cut.getMembers(room, slotId1).first()[alice to memberId] shouldBe null
        }
    }

    private fun rtcSlotEvent(slotId: RtcSlotId, content: RtcSlotEventContent) =
        StateEvent(
            content = content,
            id = EventId("\$slot-${slotId.value}"),
            sender = alice,
            roomId = room,
            originTimestamp = 1,
            stateKey = slotId.value,
        )

    private fun rtcMemberEvent(
        sender: UserId,
        memberId: RtcMemberId,
        slotId: RtcSlotId,
        stickyKey: String = memberId.value,
        application: RtcApplicationMember = CallRtcApplication.Member(),
    ) =
        MessageEvent(
            content =
                RtcMemberEventContent.Join(
                    slotId = slotId,
                    member = RtcMemberEventContent.Member(memberId),
                    application = application,
                    stickyKey = stickyKey,
                ),
            id = EventId("\$member-${sender.full}-${memberId.value}"),
            sender = sender,
            roomId = room,
            originTimestamp = 1,
            sticky = StickyEventData(60_000),
        )

    private suspend fun saveStateEvent(event: StateEvent<*>) {
        tm.writeTransaction { roomStateStore.save(event) }
    }

    private suspend fun saveRtcMemberEvent(event: MessageEvent<out RtcMemberEventContent>) {
        tm.writeTransaction {
            saveStateEvent(
                StateEvent(
                    content = MemberEventContent(membership = Membership.JOIN),
                    id = EventId("\$member"),
                    sender = event.sender,
                    roomId = room,
                    originTimestamp = 1,
                    stateKey = event.sender.full,
                )
            )
            stickyEventStore.save(
                @Suppress("UNCHECKED_CAST")
                StoredStickyEvent(
                    event = event as MessageEvent<StickyEventContent>,
                    startTime = testScope.testClock.now(),
                    endTime = testScope.testClock.now() + 1.minutes,
                )
            )
        }
    }

    private suspend fun saveEncryptionEvent() {
        saveStateEvent(
            StateEvent(
                content = EncryptionEventContent(),
                id = EventId("\$encryption"),
                sender = alice,
                roomId = room,
                originTimestamp = 1,
                stateKey = "",
            )
        )
    }
}
