package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import kotlinx.serialization.Serializable

@MSC4143 @Serializable data object PerMemberRtcEncryption : RtcEncryption
