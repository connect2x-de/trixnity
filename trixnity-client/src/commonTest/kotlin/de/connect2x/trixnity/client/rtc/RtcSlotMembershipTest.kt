package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.getInMemoryServerDataStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.RoomEventEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.RtcSlotMembershipDelegateLeaveHandlerMock
import de.connect2x.trixnity.client.mocks.RtcSlotMembershipEncryptionHandlerMock
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.client.rtc.RtcSlotMembership.State
import de.connect2x.trixnity.client.rtc.RtcSlotMembershipImpl.Companion.APPLY_REQUESTED_STATE_GRACE_PERIOD
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.room.DelayedEventAction
import de.connect2x.trixnity.clientserverapi.model.room.SendDelayedEvent
import de.connect2x.trixnity.clientserverapi.model.room.SendEventResponse
import de.connect2x.trixnity.clientserverapi.model.room.SendMessageEvent
import de.connect2x.trixnity.clientserverapi.model.server.Capabilities
import de.connect2x.trixnity.clientserverapi.model.server.Capability
import de.connect2x.trixnity.clientserverapi.model.server.GetCapabilities
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.LiveKitRtcTransport
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.fetchAndUpdate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job

@OptIn(MSC4143::class, MSC4193::class, MSC4195::class, MSC4140::class)
class RtcSlotMembershipTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val roomId = RoomId("room")
    private val slotId = RtcSlotId("slot")
    private val slotEventId = EventId("$1slot")
    private val memberId = RtcMemberId("member")
    private val userId = UserId("me", "server")
    private val application = CallRtcApplication.Member()

    private val transports =
        RtcMemberEventContent.Join.RtcTransports(published = listOf(LiveKitRtcTransport(Url("wss://example.com"))))
    private val config =
        RtcSlotMembership.Config( // custom values to distinct durations from each other
            membershipStickyDuration = 2.minutes,
            membershipStickyDurationBuffer = 10.seconds,
            joinStickyDurationFallback = 30.seconds,
            joinStickyDurationFallbackBuffer = 5.seconds,
            delayedLeaveDelayDuration = 25.seconds,
            delayedLeaveDelayDurationBuffer = 4.seconds,
        )
    private val slotEvent = MutableStateFlow<StateEvent<RtcSlotEventContent>?>(null)
    private val memberEvents =
        MutableStateFlow<Map<Pair<UserId, RtcMemberId>, MessageEvent<RtcMemberEventContent>>>(emptyMap())
    private var onCloseCalled = false
    private val onClose = { onCloseCalled = true }
    private val delegateLeaveHandler =
        RtcSlotMembershipDelegateLeaveHandlerMock().apply {
            scheduleSetup {
                returnSupports = false
                delegateLeaveCalled.clear()
            }
        }
    private val delegateLeaveHandlers = listOf<RtcSlotMembershipDelegateLeaveHandler>(delegateLeaveHandler)
    private val encryptionHandler =
        RtcSlotMembershipEncryptionHandlerMock().apply { scheduleSetup { returnSupports = false } }
    private val encryptionHandlers = listOf<RtcSlotMembershipEncryptionHandler>(encryptionHandler)
    private val roomEventEncryptionService = RoomEventEncryptionServiceMock(useInput = true)
    private val roomEventEncryptionServices = listOf<RoomEventEncryptionService>(roomEventEncryptionService)
    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)
    private val currentSyncState = CurrentSyncState(MutableStateFlow(SyncState.STARTED))
    private val serverDataStore = getInMemoryServerDataStore { tm.writeTransaction { clearCache() } }
    private val userInfo = UserInfo(userId, "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, ""))

    private val sendMessageCount = AtomicInt(0)
    private val sendMessageRequests = mutableListOf<Pair<Duration, MessageEventContent>>()
    private val sendMessageResponse = mutableListOf<Result<SendEventResponse>>()
    private val sendDelayedEventCount = AtomicInt(0)
    private val sendDelayedEventRequests = mutableListOf<SendDelayedEvent.Request>()
    private val sendDelayedEventResponse = mutableListOf<Result<SendDelayedEvent.Response>>()
    private val delayedEventActionCount = AtomicInt(0)
    private val delayedEventActionRequests = mutableListOf<Pair<DelayId, DelayedEventAction.Action>>()
    private val delayedEventActionResponse = mutableListOf<Result<Unit>>()

    private val expectedJoinContent =
        RtcMemberEventContent.Join(
            slotId = slotId,
            slotEventId = slotEventId,
            member = RtcMemberEventContent.Member(memberId),
            application = application,
            transports = transports,
            stickyKey = memberId.value,
        )

    private val expectedLeaveContent =
        RtcMemberEventContent.Leave(
            slotId = slotId,
            slotEventId = slotEventId,
            member = RtcMemberEventContent.Member(memberId),
            reason = RtcMemberEventContent.Leave.Reason("dino"),
            stickyKey = memberId.value,
        )

    private val expectedDelayedLeaveContent =
        RtcMemberEventContent.Leave(
            slotId = slotId,
            slotEventId = slotEventId,
            member = RtcMemberEventContent.Member(memberId),
            reason =
                RtcMemberEventContent.Leave.Reason(RtcApplicationMember.DefaultLeaveReasonCode.DELAYED_LEAVE.value),
            stickyKey = memberId.value,
        )

    private val expectedSlotClosedLeaveContent =
        RtcMemberEventContent.Leave(
            slotId = slotId,
            slotEventId = slotEventId,
            member = RtcMemberEventContent.Member(memberId),
            reason = RtcMemberEventContent.Leave.Reason(RtcApplicationMember.DefaultLeaveReasonCode.SLOT_CLOSED.value),
            stickyKey = memberId.value,
        )

    private val permissionException = MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.Forbidden("no"))

    private fun cut(): RtcSlotMembershipImpl {
        return RtcSlotMembershipImpl(
            roomId = roomId,
            slotId = slotId,
            slotEventId = slotEventId,
            memberId = memberId,
            application = application,
            transports = transports,
            config = config,
            slotEvent = slotEvent,
            memberEvents = memberEvents,
            onClose = onClose,
            delegateLeaveHandlers = delegateLeaveHandlers,
            encryptionHandlers = encryptionHandlers,
            roomEventEncryptionServices = roomEventEncryptionServices,
            api = api,
            currentSyncState = currentSyncState,
            serverDataStore = serverDataStore,
            userInfo = userInfo,
            clock = testScope.testClock,
            coroutineScope = testScope.backgroundScope,
        )
    }

    @BeforeTest
    fun setup() {
        onCloseCalled = false
        slotEvent.value = null
        memberEvents.value = emptyMap()
        sendMessageCount.store(0)
        sendMessageRequests.clear()
        sendMessageResponse.clear()
        sendDelayedEventCount.store(0)
        sendDelayedEventRequests.clear()
        sendDelayedEventResponse.clear()
        delayedEventActionCount.store(0)
        delayedEventActionRequests.clear()
        delayedEventActionResponse.clear()

        apiConfig.endpoints(true) {
            matrixJsonEndpoint(
                SendMessageEvent(
                    roomId,
                    "org.matrix.msc4143.rtc.member",
                    "*",
                    stickyDurationMs = config.membershipStickyDuration.inWholeMilliseconds,
                )
            ) {
                sendMessageRequests.add(config.membershipStickyDuration to it)
                sendMessageResponse[sendMessageCount.fetchAndUpdate { it + 1 }].getOrThrow()
            }
            matrixJsonEndpoint(
                SendMessageEvent(
                    roomId,
                    "org.matrix.msc4143.rtc.member",
                    "*",
                    stickyDurationMs = config.joinStickyDurationFallback.inWholeMilliseconds,
                )
            ) {
                sendMessageRequests.add(config.joinStickyDurationFallback to it)
                sendMessageResponse[sendMessageCount.fetchAndUpdate { it + 1 }].getOrThrow()
            }
            matrixJsonEndpoint(SendDelayedEvent(roomId, "org.matrix.msc4143.rtc.member", "*")) {
                sendDelayedEventRequests.add(it)
                sendDelayedEventResponse[sendDelayedEventCount.fetchAndUpdate { it + 1 }].getOrThrow()
            }
            repeat(2) { i ->
                val delayId = DelayId("delay_$i")
                matrixJsonEndpoint(DelayedEventAction(delayId, DelayedEventAction.Action.SEND)) {
                    delayedEventActionRequests.add(delayId to DelayedEventAction.Action.SEND)
                    delayedEventActionResponse[delayedEventActionCount.fetchAndUpdate { it + 1 }].getOrThrow()
                }
                matrixJsonEndpoint(DelayedEventAction(delayId, DelayedEventAction.Action.CANCEL)) {
                    delayedEventActionRequests.add(delayId to DelayedEventAction.Action.CANCEL)
                    delayedEventActionResponse[delayedEventActionCount.fetchAndUpdate { it + 1 }].getOrThrow()
                }
                matrixJsonEndpoint(DelayedEventAction(delayId, DelayedEventAction.Action.RESTART)) {
                    delayedEventActionRequests.add(delayId to DelayedEventAction.Action.RESTART)
                    delayedEventActionResponse[delayedEventActionCount.fetchAndUpdate { it + 1 }].getOrThrow()
                }
            }
        }
    }

    @Test
    fun `state - requested join - error`() = runTest {
        cut().use { cut ->
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.failure(permissionException))
            val state = backgroundScope.async { cut.state.take(2).toList() }
            delay(10.milliseconds)
            state.await() shouldBe
                listOf(
                    State.Joining(expectedJoinContent),
                    State.JoiningError(
                        expectedJoinContent,
                        RtcSlotMembership.Error.PermissionError(permissionException),
                    ),
                )
            sendMessageRequests shouldBe listOf(config.joinStickyDurationFallback to expectedJoinContent)
        }
    }

    @Test
    fun `state - requested join - error - retry`() = runTest {
        cut().use { cut ->
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.failure(permissionException))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub"))))
            val state = backgroundScope.async { cut.state.take(4).toList() }
            delay(150.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))
            delay(10.milliseconds)

            state.await() shouldBe
                listOf(
                    State.Joining(expectedJoinContent),
                    State.JoiningError(
                        expectedJoinContent,
                        RtcSlotMembership.Error.PermissionError(permissionException),
                    ),
                    State.Joining(expectedJoinContent),
                    State.Join(expectedJoinContent),
                )
            sendMessageRequests shouldBe
                listOf(
                    config.joinStickyDurationFallback to expectedJoinContent,
                    config.joinStickyDurationFallback to expectedJoinContent,
                )
        }
    }

    @Test
    fun `state - requested join - loop`() = runTest {
        cut().use { cut ->
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub1"))))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub2"))))
            val state = backgroundScope.async { cut.state.take(2).toList() }
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))

            state.await() shouldBe listOf(State.Joining(expectedJoinContent), State.Join(expectedJoinContent))
            sendMessageRequests shouldBe listOf(config.joinStickyDurationFallback to expectedJoinContent)

            delay(config.joinStickyDurationFallback - config.joinStickyDurationFallbackBuffer)
            sendMessageRequests shouldBe
                listOf(
                    config.joinStickyDurationFallback to expectedJoinContent,
                    config.joinStickyDurationFallback to expectedJoinContent,
                )
        }
    }

    @Test
    fun `state - requested join - update`() = runTest {
        cut().use { cut ->
            val expectedJoinContent2 = expectedJoinContent.copy(transports = RtcMemberEventContent.Join.RtcTransports())
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub1"))))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub2"))))
            val state = backgroundScope.async { cut.state.take(4).toList() }
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))
            delay(10.milliseconds)
            cut.update(slotEventId, application, RtcMemberEventContent.Join.RtcTransports())
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent2))

            state.await() shouldBe
                listOf(
                    State.Joining(expectedJoinContent),
                    State.Join(expectedJoinContent),
                    State.Joining(expectedJoinContent2),
                    State.Join(expectedJoinContent2),
                )
            sendMessageRequests shouldBe
                listOf(
                    config.joinStickyDurationFallback to expectedJoinContent,
                    config.joinStickyDurationFallback to expectedJoinContent2,
                )
        }
    }

    @Test
    fun `state - requested join - with delayed event - loop`() = runTest {
        enableDelayedEvents()
        cut().use { cut ->
            val delayId = DelayId("delay_0")
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub1"))))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub2"))))
            sendDelayedEventResponse.add(Result.success(SendDelayedEvent.Response(delayId)))
            repeat(6) { delayedEventActionResponse.add(Result.success(Unit)) }
            val state = backgroundScope.async { cut.state.take(2).toList() }
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))

            state.await() shouldBe listOf(State.Joining(expectedJoinContent), State.Join(expectedJoinContent))
            sendMessageRequests shouldBe listOf(config.membershipStickyDuration to expectedJoinContent)
            sendDelayedEventRequests shouldBe
                listOf(
                    SendDelayedEvent.Request.Message(
                        expectedDelayedLeaveContent,
                        config.delayedLeaveDelayDuration.inWholeMilliseconds,
                        config.membershipStickyDuration.inWholeMilliseconds,
                    )
                )
            delayedEventActionRequests shouldBe listOf()

            delay(config.delayedLeaveDelayDuration - config.delayedLeaveDelayDurationBuffer)
            sendMessageRequests shouldBe listOf(config.membershipStickyDuration to expectedJoinContent)
            sendDelayedEventRequests shouldBe
                listOf(
                    SendDelayedEvent.Request.Message(
                        expectedDelayedLeaveContent,
                        config.delayedLeaveDelayDuration.inWholeMilliseconds,
                        config.membershipStickyDuration.inWholeMilliseconds,
                    )
                )
            delayedEventActionRequests shouldBe listOf(delayId to DelayedEventAction.Action.RESTART) // changed

            delay(config.membershipStickyDuration - config.membershipStickyDurationBuffer)
            sendMessageRequests shouldBe
                listOf(
                    config.membershipStickyDuration to expectedJoinContent,
                    config.membershipStickyDuration to expectedJoinContent, // changed
                )
            sendDelayedEventRequests shouldBe
                listOf(
                    SendDelayedEvent.Request.Message(
                        expectedDelayedLeaveContent,
                        config.delayedLeaveDelayDuration.inWholeMilliseconds,
                        config.membershipStickyDuration.inWholeMilliseconds,
                    )
                )
            delayedEventActionRequests shouldBe List(6) { delayId to DelayedEventAction.Action.RESTART } // changed
            delegateLeaveHandler.delegateLeaveCalled.shouldBeEmpty()
        }
    }

    @Test
    fun `state - requested join - with delayed event - update - cancel delayed leave`() = runTest {
        enableDelayedEvents()
        cut().use { cut ->
            val expectedJoinContent2 = expectedJoinContent.copy(transports = RtcMemberEventContent.Join.RtcTransports())
            val delayId0 = DelayId("delay_0")
            val delayId1 = DelayId("delay_1")
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub1"))))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub2"))))
            sendDelayedEventResponse.add(Result.success(SendDelayedEvent.Response(delayId0)))
            sendDelayedEventResponse.add(Result.success(SendDelayedEvent.Response(delayId1)))
            repeat(2) { delayedEventActionResponse.add(Result.success(Unit)) }
            val state = backgroundScope.async { cut.state.take(4).toList() }
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))
            delay(10.milliseconds)
            cut.update(slotEventId, application, RtcMemberEventContent.Join.RtcTransports())
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent2))

            state.await() shouldBe
                listOf(
                    State.Joining(expectedJoinContent),
                    State.Join(expectedJoinContent),
                    State.Joining(expectedJoinContent2),
                    State.Join(expectedJoinContent2),
                )
            sendMessageRequests shouldBe
                listOf(
                    config.membershipStickyDuration to expectedJoinContent,
                    config.membershipStickyDuration to expectedJoinContent2,
                )
            sendDelayedEventRequests shouldBe
                listOf(
                    SendDelayedEvent.Request.Message(
                        expectedDelayedLeaveContent,
                        config.delayedLeaveDelayDuration.inWholeMilliseconds,
                        config.membershipStickyDuration.inWholeMilliseconds,
                    ),
                    SendDelayedEvent.Request.Message(
                        expectedDelayedLeaveContent,
                        config.delayedLeaveDelayDuration.inWholeMilliseconds,
                        config.membershipStickyDuration.inWholeMilliseconds,
                    ),
                )
            delayedEventActionRequests shouldBe listOf(delayId0 to DelayedEventAction.Action.CANCEL)
            delegateLeaveHandler.delegateLeaveCalled.shouldBeEmpty()
        }
    }

    @Test
    fun `state - requested join - with delayed event - delegate`() = runTest {
        enableDelayedEvents()
        cut().use { cut ->
            delegateLeaveHandler.returnSupports = true
            val delayId = DelayId("delay_0")
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub1"))))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub2"))))
            sendDelayedEventResponse.add(Result.success(SendDelayedEvent.Response(delayId)))
            repeat(6) { delayedEventActionResponse.add(Result.success(Unit)) }
            val state = backgroundScope.async { cut.state.take(2).toList() }
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))

            state.await() shouldBe listOf(State.Joining(expectedJoinContent), State.Join(expectedJoinContent))
            sendMessageRequests shouldBe listOf(config.membershipStickyDuration to expectedJoinContent)
            sendDelayedEventRequests shouldBe
                listOf(
                    SendDelayedEvent.Request.Message(
                        expectedDelayedLeaveContent,
                        config.delayedLeaveDelayDuration.inWholeMilliseconds,
                        config.membershipStickyDuration.inWholeMilliseconds,
                    )
                )
            delayedEventActionRequests shouldBe listOf()
            delegateLeaveHandler.delegateLeaveCalled shouldBe
                listOf(
                    RtcSlotMembershipDelegateLeaveHandlerMock.DelegateLeaveCall(
                        roomId = roomId,
                        slotId = slotId,
                        memberId = memberId,
                        delayId = delayId,
                        applicationId = application,
                        transports = transports,
                    )
                )

            delay(config.membershipStickyDuration - config.membershipStickyDurationBuffer)
            sendMessageRequests shouldBe
                listOf(
                    config.membershipStickyDuration to expectedJoinContent,
                    config.membershipStickyDuration to expectedJoinContent, // changed
                )
            sendDelayedEventRequests shouldBe
                listOf(
                    SendDelayedEvent.Request.Message(
                        expectedDelayedLeaveContent,
                        config.delayedLeaveDelayDuration.inWholeMilliseconds,
                        config.membershipStickyDuration.inWholeMilliseconds,
                    )
                )
            delayedEventActionRequests shouldBe listOf()
            delegateLeaveHandler.delegateLeaveCalled shouldBe
                listOf(
                    RtcSlotMembershipDelegateLeaveHandlerMock.DelegateLeaveCall(
                        roomId = roomId,
                        slotId = slotId,
                        memberId = memberId,
                        delayId = delayId,
                        applicationId = application,
                        transports = transports,
                    )
                )
        }
    }

    private suspend fun RtcSlotMembership.join(encryption: RtcEncryption? = null) {
        slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, encryption))
        sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub"))))
        sendDelayedEventResponse.add(Result.success(SendDelayedEvent.Response(DelayId("delay_0"))))
        delay(10.milliseconds)
        memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))
        delay(10.milliseconds)
        state.first().shouldBeInstanceOf<State.Join>()
        sendMessageRequests.clear()
    }

    @Test
    fun `state - requested leave - error`() = runTest {
        cut().use { cut ->
            cut.join()
            sendMessageResponse.add(Result.failure(permissionException))

            val state = backgroundScope.async { cut.state.take(3).toList() }
            delay(10.milliseconds)
            cut.leave(expectedLeaveContent.reason)
            state.await() shouldBe
                listOf(
                    State.Join(expectedJoinContent),
                    State.Leaving(expectedLeaveContent),
                    State.LeavingError(
                        expectedLeaveContent,
                        RtcSlotMembership.Error.PermissionError(permissionException),
                    ),
                )
            sendMessageRequests shouldBe listOf(config.membershipStickyDuration to expectedLeaveContent)
        }
    }

    @Test
    fun `state - requested leave - error - retry`() = runTest {
        cut().use { cut ->
            cut.join()
            sendMessageResponse.add(Result.failure(permissionException))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub"))))
            val state = backgroundScope.async { cut.state.take(5).toList() }
            cut.leave(expectedLeaveContent.reason)
            delay(150.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedLeaveContent))
            delay(10.milliseconds)

            state.await() shouldBe
                listOf(
                    State.Join(expectedJoinContent),
                    State.Leaving(expectedLeaveContent),
                    State.LeavingError(
                        expectedLeaveContent,
                        RtcSlotMembership.Error.PermissionError(permissionException),
                    ),
                    State.Leaving(expectedLeaveContent),
                    State.Leave(expectedLeaveContent),
                )
            sendMessageRequests shouldBe
                listOf(
                    config.membershipStickyDuration to expectedLeaveContent,
                    config.membershipStickyDuration to expectedLeaveContent,
                )
            onCloseCalled shouldBe true
        }
    }

    @Test
    fun `state - requested leave - leave`() = runTest {
        cut().use { cut ->
            cut.join()
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub"))))
            val state = backgroundScope.async { cut.state.take(3).toList() }
            cut.leave(expectedLeaveContent.reason)
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedLeaveContent))

            state.await() shouldBe
                listOf(
                    State.Join(expectedJoinContent),
                    State.Leaving(expectedLeaveContent),
                    State.Leave(expectedLeaveContent),
                )
            sendMessageRequests shouldBe listOf(config.membershipStickyDuration to expectedLeaveContent)
            onCloseCalled shouldBe true
        }
    }

    @Test
    fun `state - requested leave - with delayed event - cancel delayed leave`() = runTest {
        enableDelayedEvents()
        cut().use { cut ->
            cut.join()
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub"))))
            delayedEventActionResponse.add(Result.success(Unit))
            val state = backgroundScope.async { cut.state.take(3).toList() }
            cut.leave(expectedLeaveContent.reason)
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedLeaveContent))

            state.await() shouldBe
                listOf(
                    State.Join(expectedJoinContent),
                    State.Leaving(expectedLeaveContent),
                    State.Leave(expectedLeaveContent),
                )
            sendMessageRequests shouldBe listOf(config.membershipStickyDuration to expectedLeaveContent)
            delayedEventActionRequests shouldBe listOf(DelayId("delay_0") to DelayedEventAction.Action.CANCEL)
            onCloseCalled shouldBe true
        }
    }

    @Test
    fun `state - slot closed - leave`() = runTest {
        enableDelayedEvents()
        cut().use { cut ->
            cut.join()
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub"))))
            delayedEventActionResponse.add(Result.success(Unit))
            val state = backgroundScope.async { cut.state.take(3).toList() }
            slotEvent.value = slotEvent(RtcSlotEventContent.Closed())
            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedSlotClosedLeaveContent))

            state.await() shouldBe
                listOf(
                    State.Join(expectedJoinContent),
                    State.Leaving(expectedSlotClosedLeaveContent),
                    State.Leave(expectedSlotClosedLeaveContent),
                )
            sendMessageRequests shouldBe listOf(config.membershipStickyDuration to expectedSlotClosedLeaveContent)
            delayedEventActionRequests shouldBe listOf(DelayId("delay_0") to DelayedEventAction.Action.CANCEL)
            onCloseCalled shouldBe true
        }
    }

    @Test
    fun `continuouslyApplyRequestedState - retry when state changed after initial delay`() = runTest {
        cut().use { cut ->
            slotEvent.value = slotEvent(RtcSlotEventContent.Open(CallRtcApplication.Slot, null))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub"))))
            sendMessageResponse.add(Result.success(SendEventResponse(EventId("blub2"))))

            delay(10.milliseconds)
            memberEvents.value = mapOf(Pair(userId, memberId) to memberEvent(expectedJoinContent))
            delay(10.milliseconds)

            sendMessageRequests shouldBe listOf(config.joinStickyDurationFallback to expectedJoinContent)

            delay(APPLY_REQUESTED_STATE_GRACE_PERIOD + 10.milliseconds)

            memberEvents.value = emptyMap()
            delay(10.milliseconds)

            sendMessageRequests shouldBe
                listOf(
                    config.joinStickyDurationFallback to expectedJoinContent,
                    config.joinStickyDurationFallback to expectedJoinContent, // this is the retry
                )
        }
    }

    @Test
    fun `close - stops job and calls onClose`() = runTest {
        cut().close()
        delay(20.milliseconds)
        testScope.backgroundScope.coroutineContext.job.children.count() shouldBe 1
        onCloseCalled shouldBe true
    }

    @Test
    fun `encryptionInfo - select and use matching handler`() = runTest {
        encryptionHandler.returnSupports = true
        cut().use { cut ->
            val encryptionInfo = object : RtcSlotMembershipEncryptionInfo {}
            cut.join(object : RtcEncryption {})
            val result = backgroundScope.async { cut.encryptionInfo.first() }
            encryptionHandler.getEncryptionInfo.emit(encryptionInfo)
            result.await() shouldBe encryptionInfo
        }
    }

    @Test
    fun `encryptionInfo - slot encryption null - no encryption`() = runTest {
        encryptionHandler.returnSupports = true
        cut().use { cut ->
            val encryptionInfo = object : RtcSlotMembershipEncryptionInfo {}
            cut.join(null)
            val result = backgroundScope.async { cut.encryptionInfo.first() }
            encryptionHandler.getEncryptionInfo.emit(encryptionInfo)
            result.await() shouldBe NoRtcSlotMembershipEncryptionInfo
        }
    }

    @Test
    fun `encryptionInfo - not joined - no encryption`() = runTest {
        encryptionHandler.returnSupports = true
        cut().use { cut ->
            val encryptionInfo = object : RtcSlotMembershipEncryptionInfo {}
            val result = backgroundScope.async { cut.encryptionInfo.first() }
            encryptionHandler.getEncryptionInfo.emit(encryptionInfo)
            result.await() shouldBe NoRtcSlotMembershipEncryptionInfo
        }
    }

    private fun slotEvent(content: RtcSlotEventContent): StateEvent<RtcSlotEventContent> =
        StateEvent(content, EventId("\$SlotEvent"), userId, roomId, 1234, stateKey = slotId.value)

    private fun memberEvent(content: RtcMemberEventContent): MessageEvent<RtcMemberEventContent> =
        MessageEvent(content, EventId("\$SlotEvent"), userId, roomId, 1234)

    private suspend fun enableDelayedEvents() {
        val oldServerData = serverDataStore.getServerData()

        tm.writeTransaction {
            serverDataStore.setServerData(
                oldServerData.copy(
                    capabilities =
                        GetCapabilities.Response(
                            Capabilities(setOf(Capability.DelayedEvents(maxDelayMs = 60_000, maxScheduled = 10)))
                        )
                )
            )
        }
    }
}
