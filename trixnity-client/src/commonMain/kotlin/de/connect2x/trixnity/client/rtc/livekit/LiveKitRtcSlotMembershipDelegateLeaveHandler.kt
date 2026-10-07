package de.connect2x.trixnity.client.rtc.livekit

import de.connect2x.trixnity.client.rtc.RtcSlotMembershipDelegateLeaveHandler
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.m.rtc.LiveKitRtcTransport
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId

@MSC4143
@MSC4195
class LiveKitRtcSlotMembershipDelegateLeaveHandler(
    private val api: MatrixClientServerApiClient,
    private val userInfo: UserInfo,
) : RtcSlotMembershipDelegateLeaveHandler {
    override fun supports(
        application: RtcApplicationMember,
        transports: RtcMemberEventContent.Join.RtcTransports?,
    ): Boolean =
        transports?.published?.any {
            it is LiveKitRtcTransport && (it.serverName == null || it.serverName == userInfo.userId.domain)
        } == true

    override suspend fun delegateLeave(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        delayId: DelayId,
        application: RtcApplicationMember,
        transports: RtcMemberEventContent.Join.RtcTransports?,
    ): Result<Unit> {
        val transportUrls =
            transports
                ?.published
                ?.filterIsInstance<LiveKitRtcTransport>()
                ?.filter { (it.serverName == null || it.serverName == userInfo.userId.domain) }
                ?.map { it.url }
                ?.toSet()
                .orEmpty()
        val results = transportUrls.map { transportUrl ->
            api.rtc.delegateDelayedLeave(
                url = transportUrl,
                roomId = roomId,
                slotId = slotId,
                memberId = memberId,
                delayId = delayId,
            )
        }
        return if (results.isNotEmpty() && results.all { it.isFailure }) results.first() else Result.success(Unit)
    }
}
