package de.connect2x.trixnity.client.rtc

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow

private val log = Logger("de.connect2x.trixnity.client.rtc.RtcSlotMembership")

@MSC4143
interface RtcSlotMembershipFactory {
    fun create(
        roomId: RoomId,
        slotId: RtcSlotId,
        slotEventId: EventId,
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

    fun update(
        slotEventId: EventId,
        application: RtcApplicationMember,
        transports: RtcMemberEventContent.Join.RtcTransports,
    )

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
    )
}
