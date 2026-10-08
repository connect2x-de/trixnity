package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@MSC4143
// TODO remove Element flavor: use this instead
// data class RtcEncryptionKeyEventContent(
//    @SerialName("room_id") val roomId: RoomId,
//    @SerialName("member_id") val memberId: RtcMemberId,
//    @SerialName("media_key") val mediaKey: MediaKey,
// )
data class RtcEncryptionKeyEventContent(
    @SerialName("room_id") val roomId: RoomId,
    @SerialName("member_id") val memberId: RtcMemberId? = null,
    @SerialName("member") val member: Member? = null,
    @SerialName("media_key") val mediaKey: MediaKey? = null,
    @SerialName("keys") val keys: MediaKey? = null,
) : ToDeviceEventContent {
    // TODO remove Element flavor: no session
    @SerialName("session") @Deprecated("Element flavor - never use!") val session: Session = Session()
    // TODO remove Element flavor: no sentTs
    @SerialName("sent_ts")
    @Deprecated("Element flavor - never use!")
    val sentTs: Long = Clock.System.now().toEpochMilliseconds()

    // TODO remove Element flavor: no Member
    @Serializable
    data class Member(
        @SerialName("id") val id: RtcMemberId,
        @SerialName("claimed_device_id") val claimedDeviceId: String,
    )

    // TODO remove Element flavor: no Session
    @Serializable
    data class Session(
        @SerialName("call_id") val callId: String = "",
        @SerialName("application") val application: String = "m.call",
        @SerialName("scope") val scope: String = "m.room",
    )

    @Serializable data class MediaKey(@SerialName("key") val key: String, @SerialName("index") val index: Long)
}
