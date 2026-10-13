package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.rtc.RtcSlotMembershipEncryptionConfig
import de.connect2x.trixnity.client.rtc.RtcSlotMembershipEncryptionHandler
import de.connect2x.trixnity.client.rtc.RtcSlotMembershipEncryptionInfo
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@MSC4143
class RtcSlotMembershipEncryptionHandlerMock : RtcSlotMembershipEncryptionHandler {
    var returnSupports = false

    override fun supports(encryption: RtcEncryption, transports: RtcMemberEventContent.Join.RtcTransports?): Boolean =
        returnSupports

    val getEncryptionInfo = MutableSharedFlow<RtcSlotMembershipEncryptionInfo>()

    override fun getEncryptionInfo(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        encryption: RtcEncryption,
        transports: RtcMemberEventContent.Join.RtcTransports?,
        config: Map<RtcEncryption, RtcSlotMembershipEncryptionConfig>,
        memberEvents: Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>,
    ): Flow<RtcSlotMembershipEncryptionInfo> = getEncryptionInfo
}
