package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId

@MSC4143
interface RtcSlotMembershipDelegateLeaveHandler {
    fun supports(application: RtcApplicationMember, transports: RtcMemberEventContent.Join.RtcTransports?): Boolean

    suspend fun delegateLeave(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        delayId: DelayId,
        application: RtcApplicationMember,
        transports: RtcMemberEventContent.Join.RtcTransports?,
    ): Result<Unit>
}
