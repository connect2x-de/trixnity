package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@MSC4143
@MSC4195
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LiveKitRtcTransport(@JsonNames("livekit_service_url") @SerialName("url") val url: Url) : RtcTransport
