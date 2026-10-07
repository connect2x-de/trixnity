package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.rtc.RtcSlotMembershipDelegateLeaveHandler
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId

@MSC4143
class RtcSlotMembershipDelegateLeaveHandlerMock : RtcSlotMembershipDelegateLeaveHandler {
    var returnSupports = false

    override fun supports(
        application: RtcApplicationMember,
        transports: RtcMemberEventContent.Join.RtcTransports?,
    ): Boolean = returnSupports

    var returnDelegateLeave = Result.success(Unit)
    val delegateLeaveCalled = mutableListOf<DelegateLeaveCall>()

    data class DelegateLeaveCall(
        val roomId: RoomId,
        val slotId: RtcSlotId,
        val memberId: RtcMemberId,
        val delayId: DelayId,
        val applicationId: RtcApplicationMember,
        val transports: RtcMemberEventContent.Join.RtcTransports?,
    )

    override suspend fun delegateLeave(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        delayId: DelayId,
        application: RtcApplicationMember,
        transports: RtcMemberEventContent.Join.RtcTransports?,
    ): Result<Unit> {
        delegateLeaveCalled.add(
            DelegateLeaveCall(
                roomId = roomId,
                slotId = slotId,
                memberId = memberId,
                delayId = delayId,
                applicationId = application,
                transports = transports,
            )
        )
        return returnDelegateLeave
    }
}
