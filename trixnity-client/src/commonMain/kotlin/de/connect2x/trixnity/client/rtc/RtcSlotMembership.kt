package de.connect2x.trixnity.client.rtc

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.flatMap
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.client.room.encrypt
import de.connect2x.trixnity.client.store.ServerDataStore
import de.connect2x.trixnity.client.utils.retry
import de.connect2x.trixnity.client.utils.retryLoop
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.model.room.DelayedEventAction
import de.connect2x.trixnity.clientserverapi.model.server.delayedEvents
import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.utils.nextString
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

private val log = Logger("de.connect2x.trixnity.client.rtc.RtcSlotMembership")

@MSC4143
interface RtcSlotMembershipFactory {
    fun create(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        application: RtcApplicationMember,
        transports: RtcMemberEventContent.Join.RtcTransports?,
        config: RtcSlotMembership.Config,
        slotEvent: Flow<ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>?>,
        memberEvents: Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>,
        onClose: () -> Unit,
    ): RtcSlotMembership
}

@MSC4143
interface RtcSlotMembership : AutoCloseable {
    val roomId: RoomId
    val slotId: RtcSlotId
    val memberId: RtcMemberId

    val state: Flow<State>
    val encryptionInfo: Flow<RtcSlotMembershipEncryptionInfo>

    fun update(application: RtcApplicationMember, transports: RtcMemberEventContent.Join.RtcTransports)

    fun leave(reason: RtcMemberEventContent.Leave.Reason?)

    sealed interface State {
        val content: RtcMemberEventContent

        data class Joining(override val content: RtcMemberEventContent.Join) : State

        data class JoiningError(override val content: RtcMemberEventContent.Join, val error: Error) : State

        data class Join(override val content: RtcMemberEventContent.Join) : State

        data class Leaving(override val content: RtcMemberEventContent.Leave) : State

        data class LeavingError(override val content: RtcMemberEventContent.Leave, val error: Error) : State

        data class Leave(override val content: RtcMemberEventContent.Leave) : State
    }

    sealed interface Error {
        data class NetworkError(val error: Throwable) : Error

        data class PermissionError(val error: MatrixServerException) : Error
    }

    data class Config(
        val membershipStickyDuration: Duration = 1.hours,
        val membershipStickyDurationBuffer: Duration = 1.minutes,
        val joinStickyDurationFallback: Duration = 30.seconds,
        val joinStickyDurationFallbackBuffer: Duration = 5.seconds,
        val delayedLeaveDelayDuration: Duration = 30.seconds,
        val delayedLeaveDelayDurationBuffer: Duration = 5.seconds,
        val encryption: Map<RtcEncryption, RtcSlotMembershipEncryptionConfig> = emptyMap(),
    )
}

@MSC4143
@OptIn(MSC4354::class, MSC4140::class)
class RtcSlotMembershipImpl(
    // factory section
    override val roomId: RoomId,
    override val slotId: RtcSlotId,
    override val memberId: RtcMemberId,
    application: RtcApplicationMember,
    transports: RtcMemberEventContent.Join.RtcTransports?,
    private val config: RtcSlotMembership.Config,
    private val slotEvent: Flow<ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>?>,
    private val memberEvents:
        Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>,
    private val onClose: () -> Unit,
    // di section
    private val delegateLeaveHandlers: List<RtcSlotMembershipDelegateLeaveHandler>,
    private val encryptionHandlers: List<RtcSlotMembershipEncryptionHandler>,
    private val roomEventEncryptionServices: List<RoomEventEncryptionService>,
    private val api: MatrixClientServerApiClient,
    private val currentSyncState: CurrentSyncState,
    private val serverDataStore: ServerDataStore,
    private val userInfo: UserInfo,
    private val clock: Clock,
    coroutineScope: CoroutineScope,
) : RtcSlotMembership {
    private val initialRequestedStateContent =
        RtcMemberEventContent.Join(
            slotId = slotId,
            member = RtcMemberEventContent.Member(memberId, userInfo.userId, userInfo.deviceId),
            application = application,
            transports = transports,
            stickyKey = memberId.value,
        )
    private val requestedStateContent = MutableStateFlow<RtcMemberEventContent>(initialRequestedStateContent)
    private val activeStateContent =
        memberEvents.map { it[userInfo.userId to memberId]?.content }.distinctUntilChanged()
    private val errorState = MutableStateFlow<RtcSlotMembership.Error?>(null)

    private val _encryptionInfo = MutableSharedFlow<RtcSlotMembershipEncryptionInfo>(replay = 1)
    @OptIn(ExperimentalCoroutinesApi::class) override val encryptionInfo = _encryptionInfo.asFlow()
    private val job: Job

    private val _state = MutableSharedFlow<RtcSlotMembership.State>(replay = 1)
    @OptIn(ExperimentalCoroutinesApi::class) override val state = _state.asFlow()

    init {
        job = coroutineScope.launch {
            launch { collectState() }
            launch { collectEncryptionInfo() }
            launch { continuouslyApplyRequestedState() }
            launch { leaveOnSlotClosed() }
        }
    }

    companion object {
        val CLOSE_ON_LEAVE_TIMEOUT = 10.seconds
        val APPLY_REQUESTED_STATE_GRACE_PERIOD = 5.seconds
    }

    override fun close() {
        job.cancel()
        onClose()
    }

    private suspend fun collectState() {
        combine(activeStateContent, requestedStateContent, errorState) {
                activeStateContent,
                requestedStateContent,
                errorState ->
                when (requestedStateContent) {
                    is RtcMemberEventContent.Join -> {
                        when {
                            errorState != null ->
                                RtcSlotMembership.State.JoiningError(requestedStateContent, errorState)
                            activeStateContent == requestedStateContent ->
                                RtcSlotMembership.State.Join(requestedStateContent)
                            else -> RtcSlotMembership.State.Joining(requestedStateContent)
                        }
                    }
                    is RtcMemberEventContent.Leave -> {
                        when {
                            errorState != null ->
                                RtcSlotMembership.State.LeavingError(requestedStateContent, errorState)
                            activeStateContent == requestedStateContent ->
                                RtcSlotMembership.State.Leave(requestedStateContent)
                            else -> RtcSlotMembership.State.Leaving(requestedStateContent)
                        }
                    }
                }.also {
                    log.trace {
                        """
                            state:
                                activeStateContent=$activeStateContent
                                requestedStateContent=$requestedStateContent
                                errorState=$errorState
                                state=$it
                         """
                            .trimIndent()
                    }
                }
            }
            .distinctUntilChanged()
            .collect(_state)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun collectEncryptionInfo() {
        combine(slotEvent, requestedStateContent) { slotEvent, requestedStateContent ->
                slotEvent to requestedStateContent
            }
            .flatMapLatest { (slotEvent, requestedStateContent) ->
                val slotEventContent = slotEvent?.content
                val encryption = slotEventContent?.encryption
                if (
                    slotEventContent == null ||
                        slotEventContent !is RtcSlotEventContent.Open ||
                        requestedStateContent !is RtcMemberEventContent.Join ||
                        encryption == null
                )
                    return@flatMapLatest flowOf(NoRtcSlotMembershipEncryptionInfo)
                encryptionHandlers
                    .firstOrNull { it.supports(encryption = encryption, transports = requestedStateContent.transports) }
                    ?.getEncryptionInfo(
                        roomId = roomId,
                        slotId = slotId,
                        memberId = memberId,
                        encryption = encryption,
                        transports = requestedStateContent.transports,
                        config = config.encryption,
                        memberEvents = memberEvents,
                    ) ?: flowOf(NoRtcSlotMembershipEncryptionInfo)
            }
            .distinctUntilChanged()
            .collect(_encryptionInfo)
    }

    // TODO handle redactions on moderation/force leave
    private suspend fun continuouslyApplyRequestedState() {
        val leaveDelayId = AtomicReference<DelayId?>(null)
        requestedStateContent.collectLatest { requestedStateContent ->
            while (currentCoroutineContext().isActive) {
                coroutineScope {
                    select {
                        launch { applyRequestedState(requestedStateContent, leaveDelayId) }.onJoin {}
                        launch {
                            delay(APPLY_REQUESTED_STATE_GRACE_PERIOD)
                            activeStateContent.first { it != requestedStateContent }
                        }
                            .onJoin {}
                    }
                    currentCoroutineContext().cancelChildren()
                }
            }
        }
    }

    private suspend fun applyRequestedState(
        requestedStateContent: RtcMemberEventContent,
        leaveDelayId: AtomicReference<DelayId?>,
    ) {
        val oldLeaveDelayId = leaveDelayId.load()
        if (oldLeaveDelayId != null) {
            cancelOldDelayedLeave(oldLeaveDelayId)
        }
        when (requestedStateContent) {
            is RtcMemberEventContent.Join -> {
                val newLeaveDelayId =
                    createDelayedLeave(
                        stickyDuration = config.membershipStickyDuration,
                        delayDuration = config.delayedLeaveDelayDuration,
                    )
                leaveDelayId.store(newLeaveDelayId)
                coroutineScope {
                    launch {
                        joinAndKeepJoinAlive(
                            requestedStateContent = requestedStateContent,
                            stickyDuration =
                                if (newLeaveDelayId != null) config.membershipStickyDuration
                                else config.joinStickyDurationFallback,
                            stickyDurationBuffer =
                                if (newLeaveDelayId != null) config.membershipStickyDurationBuffer
                                else config.joinStickyDurationFallbackBuffer,
                        )
                    }
                    if (newLeaveDelayId != null) {
                        launch {
                            val delegateLeaveHandlers = delegateLeaveHandlers.filter {
                                it.supports(
                                    application = requestedStateContent.application,
                                    transports = requestedStateContent.transports,
                                )
                            }

                            if (delegateLeaveHandlers.isNotEmpty()) {
                                delegateLeaveHandlers.forEach { delegateLeaveHandler ->
                                    delegateDelayedLeave(delegateLeaveHandler, requestedStateContent, newLeaveDelayId)
                                }
                            } else {
                                restartDelayedLeave(
                                    leaveDelayId = newLeaveDelayId,
                                    delayDuration = config.delayedLeaveDelayDuration,
                                    delayDurationBuffer = config.delayedLeaveDelayDurationBuffer,
                                )
                            }
                        }
                    }
                }
            }
            is RtcMemberEventContent.Leave -> {
                val leaveTransactionId = SecureRandom.nextString(22)
                sendLeave(
                    requestedStateContent = requestedStateContent,
                    leaveTransactionId = leaveTransactionId,
                    stickyDuration = config.membershipStickyDuration,
                )
                withTimeoutOrNull(CLOSE_ON_LEAVE_TIMEOUT) { _state.first { it is RtcSlotMembership.State.Leave } }
                close()
            }
        }
    }

    private suspend fun cancelOldDelayedLeave(oldLeaveDelayId: DelayId) {
        currentSyncState.retry(
            onError = { error, delay ->
                log.warn(error) { "failed cancel old delayed leave for $slotId in $roomId, try again in $delay" }
            }
        ) {
            log.debug { "cancel old delayed leave for $slotId in $roomId" }
            api.room
                .delayedEventAction(delayId = oldLeaveDelayId, action = DelayedEventAction.Action.CANCEL)
                .fold(
                    onSuccess = { errorState.value = null },
                    onFailure = { exception ->
                        exception.handleCancellationAndNetworkError()
                        log.warn(exception) { "finally failed cancel old delayed leave for $slotId in $roomId" }
                    },
                )
        }
    }

    private suspend fun createDelayedLeave(
        stickyDuration: Duration,
        delayDuration: Duration,
    ): DelayId? {
        if (serverDataStore.getServerData().capabilities?.capabilities?.delayedEvents == null) return null
        val leaveTransactionId = SecureRandom.nextString(22)
        return currentSyncState.retry(
            onError = { error, delay ->
                log.warn(error) { "failed send delayed leave for $slotId in $roomId, try again in $delay" }
            }
        ) {
            log.debug { "send delayed leave for $slotId in $roomId" }
            sendDelayedStickyEvent(
                    eventContent =
                        RtcMemberEventContent.Leave(
                            slotId = slotId,
                            member = RtcMemberEventContent.Member(memberId, userInfo.userId, userInfo.deviceId),
                            reason =
                                RtcMemberEventContent.Leave.Reason(
                                    RtcApplicationMember.DefaultLeaveReasonCode.DELAYED_LEAVE.value
                                ),
                            stickyKey = memberId.value,
                        ),
                    transactionId = leaveTransactionId,
                    stickyDuration = stickyDuration,
                    delayDuration = delayDuration,
                )
                .fold(
                    onSuccess = {
                        errorState.value = null
                        it
                    },
                    onFailure = { exception ->
                        exception.handleCancellationAndNetworkError()
                        exception.handlePermissionError()
                        log.warn(exception) { "finally failed send delayed leave for $slotId in $roomId" }
                        null
                    },
                )
        }
    }

    private suspend fun joinAndKeepJoinAlive(
        requestedStateContent: RtcMemberEventContent.Join,
        stickyDuration: Duration,
        stickyDurationBuffer: Duration,
    ) {
        currentSyncState.retryLoop(
            onError = { error, delay ->
                log.warn(error) { "failed send join for $slotId in $roomId, try again in $delay" }
            }
        ) {
            log.debug { "send join for $slotId in $roomId" }
            val joinTransactionId = SecureRandom.nextString(22)
            sendStickyEvent(
                    eventContent = requestedStateContent,
                    transactionId = joinTransactionId,
                    stickyDuration = stickyDuration,
                )
                .fold(
                    onSuccess = { errorState.value = null },
                    onFailure = { exception ->
                        exception.handleCancellationAndNetworkError()
                        exception.handlePermissionError()
                        throw exception
                    },
                )
            sleepAwareDelay(stickyDuration - stickyDurationBuffer)
        }
    }

    private suspend fun restartDelayedLeave(
        leaveDelayId: DelayId,
        delayDuration: Duration,
        delayDurationBuffer: Duration,
    ) {
        sleepAwareDelay(delayDuration - delayDurationBuffer)
        currentSyncState.retryLoop(
            onError = { error, delay ->
                log.warn(error) { "failed restart delayed leave for $slotId in $roomId, try again in $delay" }
            }
        ) {
            log.debug { "restart delayed leave for $slotId in $roomId" }
            api.room
                .delayedEventAction(delayId = leaveDelayId, action = DelayedEventAction.Action.RESTART)
                .fold(
                    onSuccess = { errorState.value = null },
                    onFailure = { exception ->
                        exception.handleCancellationAndNetworkError()
                        exception.handlePermissionError()
                        throw exception
                    },
                )
            sleepAwareDelay(delayDuration - delayDurationBuffer)
        }
    }

    private suspend fun sleepAwareDelay(duration: Duration) {
        val endTime = clock.now() + duration
        while (currentCoroutineContext().isActive) {
            val durationToEndTime = endTime - clock.now()
            if (durationToEndTime <= Duration.ZERO) {
                break
            }
            delay(durationToEndTime.coerceAtMost(1.seconds)) // to catch sleeping scheduler cases
            if (endTime < clock.now()) {
                break
            }
        }
    }

    private suspend fun delegateDelayedLeave(
        delegateLeaveHandler: RtcSlotMembershipDelegateLeaveHandler,
        requestedStateContent: RtcMemberEventContent.Join,
        leaveDelayId: DelayId,
    ) {
        currentSyncState.retry(
            onError = { error, delay ->
                log.warn(error) { "failed delegate restart delayed leave for $slotId in $roomId, try again in $delay" }
            }
        ) {
            log.debug { "delegate restart delayed leave for $slotId in $roomId" }
            delegateLeaveHandler
                .delegateLeave(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = memberId,
                    delayId = leaveDelayId,
                    application = requestedStateContent.application,
                    transports = requestedStateContent.transports,
                )
                .fold(
                    onSuccess = { errorState.value = null },
                    onFailure = { exception ->
                        exception.handleCancellationAndNetworkError()
                        exception.handlePermissionError()
                        throw exception
                    },
                )
        }
    }

    private suspend fun sendLeave(
        requestedStateContent: RtcMemberEventContent.Leave,
        leaveTransactionId: String,
        stickyDuration: Duration,
    ) {
        currentSyncState.retry(
            onError = { error, delay ->
                log.warn(error) { "failed send leave for $slotId in $roomId, try again in $delay" }
            }
        ) {
            log.debug { "send leave for $slotId in $roomId" }
            sendStickyEvent(
                    eventContent = requestedStateContent,
                    transactionId = leaveTransactionId,
                    stickyDuration = stickyDuration,
                )
                .fold(
                    onSuccess = {
                        errorState.value = null
                        it
                    },
                    onFailure = { exception ->
                        exception.handleCancellationAndNetworkError()
                        exception.handlePermissionError()
                        null
                    },
                )
        }
    }

    private suspend fun leaveOnSlotClosed() {
        slotEvent.collect { event ->
            when (event?.content) {
                is RtcSlotEventContent.Open -> {}
                is RtcSlotEventContent.Closed,
                null -> {
                    leave(
                        RtcMemberEventContent.Leave.Reason(
                            RtcApplicationMember.DefaultLeaveReasonCode.SLOT_CLOSED.value
                        )
                    )
                }
            }
        }
    }

    override fun update(application: RtcApplicationMember, transports: RtcMemberEventContent.Join.RtcTransports) {
        val joinContent =
            RtcMemberEventContent.Join(
                slotId = slotId,
                member = RtcMemberEventContent.Member(memberId, userInfo.userId, userInfo.deviceId),
                application = application,
                transports = transports,
                stickyKey = memberId.value,
            )
        log.debug { "request join update for $slotId in $roomId" }
        requestedStateContent.setOrSkip(joinContent)
    }

    override fun leave(reason: RtcMemberEventContent.Leave.Reason?) {
        val leaveContent =
            RtcMemberEventContent.Leave(
                slotId = slotId,
                member = RtcMemberEventContent.Member(memberId, userInfo.userId, userInfo.deviceId),
                reason = reason,
                stickyKey = memberId.value,
            )
        log.debug { "request leave for $slotId in $roomId, because $reason" }
        requestedStateContent.setOrSkip(leaveContent)
    }

    private suspend fun sendStickyEvent(
        eventContent: RtcMemberEventContent,
        transactionId: String,
        stickyDuration: Duration,
    ): Result<EventId> =
        encryptEventContent(roomId, eventContent).flatMap { encryptedEventContent ->
            api.room.sendMessageEvent(
                roomId = roomId,
                eventContent = encryptedEventContent,
                txnId = transactionId,
                stickyDurationMs = stickyDuration.inWholeMilliseconds,
            )
        }

    private suspend fun sendDelayedStickyEvent(
        eventContent: RtcMemberEventContent,
        transactionId: String,
        stickyDuration: Duration,
        delayDuration: Duration,
    ): Result<DelayId> =
        encryptEventContent(roomId, eventContent).flatMap { encryptedEventContent ->
            api.room.sendDelayedMessageEvent(
                roomId = roomId,
                txnId = transactionId,
                delayMs = delayDuration.inWholeMilliseconds,
                stickyDurationMs = stickyDuration.inWholeMilliseconds,
                eventContent = encryptedEventContent,
            )
        }

    private suspend fun encryptEventContent(
        roomId: RoomId,
        eventContent: RtcMemberEventContent,
    ): Result<MessageEventContent> =
        roomEventEncryptionServices.encrypt(eventContent, roomId)
            ?: Result.failure(IllegalStateException("Unable to encrypt event since the algorithm is not supported!"))

    private fun MutableStateFlow<RtcMemberEventContent>.setOrSkip(new: RtcMemberEventContent) = update {
        (it as? RtcMemberEventContent.Leave) ?: new
    }

    private fun Throwable.handleCancellationAndNetworkError() {
        if (this is CancellationException) throw this
        if (this !is MatrixServerException) {
            errorState.value = RtcSlotMembership.Error.NetworkError(this)
            throw this
        }
    }

    private fun Throwable.handlePermissionError() {
        if (this is MatrixServerException && statusCode == HttpStatusCode.Forbidden) {
            errorState.value = RtcSlotMembership.Error.PermissionError(this)
            throw this
        }
    }
}
