package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import kotlinx.serialization.json.JsonObject

@MSC4143
interface RtcApplicationSlot {
    val type: String

    @MSC4143 data class Unknown(override val type: String, val raw: JsonObject) : RtcApplicationSlot
}

@MSC4143
interface RtcApplicationMember {
    val type: String

    @MSC4143
    enum class DefaultLeaveReasonCode(val value: String) {
        LEAVE("leave"),
        DELAYED_LEAVE("delayed_leave"),
        SLOT_CLOSED("slot_closed"),
    }

    @MSC4143 data class Unknown(override val type: String, val raw: JsonObject) : RtcApplicationMember
}
