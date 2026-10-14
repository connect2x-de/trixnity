package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import kotlinx.coroutines.flow.Flow

@MSC4143 interface RtcSlotMembershipEncryptionInfo

@MSC4143 object NoRtcSlotMembershipEncryptionInfo : RtcSlotMembershipEncryptionInfo

@MSC4143 interface RtcSlotMembershipEncryptionConfig

@MSC4143
interface RtcSlotMembershipEncryptionHandler {
    fun supports(encryption: RtcEncryption, transports: RtcMemberEventContent.Join.RtcTransports?): Boolean

    fun getEncryptionInfo(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        encryption: RtcEncryption,
        transports: RtcMemberEventContent.Join.RtcTransports?,
        config: Map<RtcEncryption, RtcSlotMembershipEncryptionConfig>,
        memberEvents: Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>,
    ): Flow<RtcSlotMembershipEncryptionInfo>
}
