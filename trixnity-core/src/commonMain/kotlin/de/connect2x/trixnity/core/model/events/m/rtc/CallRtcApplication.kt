package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNames

data object CallRtcApplication {
    const val APPLICATION_TYPE = "m.call"

    // TODO remove Element flavor: use room instead of ROOM
    val SLOT_ID = RtcSlotId(APPLICATION_TYPE, "ROOM")

    @MSC4193
    @MSC4143
    @Serializable
    data object Slot : RtcApplicationSlot {
        override val type = APPLICATION_TYPE
    }

    @OptIn(ExperimentalSerializationApi::class)
    @MSC4193
    @MSC4143
    @Serializable
    data class Member(
        // TODO remove Element flavor: no m.call.intent
        @JsonNames("m.call.intent") @SerialName("intent") val intent: Intent? = null,
        @SerialName("capabilities") val capabilities: Set<Capability>? = null,
    ) : RtcApplicationMember {
        // TODO remove Element flavor: no callIntent
        @SerialName("m.call.intent")
        @Deprecated("Element flavor - never use!")
        val callIntent: String? =
            when (intent) {
                Intent.AUDIO -> "m.audio"
                Intent.VIDEO -> "m.video"
                null -> null
            }

        override val type = APPLICATION_TYPE

        @MSC4143
        enum class LeaveReasonCode(val value: String) {
            TRANSPORT_ERROR("transport_error"),
            MEDIA_ERROR("media_error"),
            CODE_MISMATCH("codec_mismatch"),
        }

        @Serializable
        enum class Intent {
            // TODO remove Element flavor: no m.audio
            @JsonNames("m.audio") @SerialName("audio") AUDIO,
            // TODO remove Element flavor: no m.video
            @JsonNames("m.video") @SerialName("video") VIDEO,
        }

        @Serializable(with = Capability.Serializer::class)
        sealed interface Capability {
            val value: String

            object RenderAudio : Capability {
                override val value: String = "m.render_audio"
            }

            object RenderVideo : Capability {
                override val value: String = "m.render_video"
            }

            data class Unknown(override val value: String) : Capability

            object Serializer : KSerializer<Capability> {
                @OptIn(InternalSerializationApi::class)
                override val descriptor: SerialDescriptor =
                    buildSerialDescriptor("CallRtcApplication.Member.Capability", PrimitiveKind.STRING)

                override fun serialize(encoder: Encoder, value: Capability) = encoder.encodeString(value.value)

                override fun deserialize(decoder: Decoder): Capability =
                    when (val usageString = decoder.decodeString()) {
                        RenderAudio.value -> RenderAudio
                        RenderVideo.value -> RenderVideo
                        else -> Unknown(usageString)
                    }
            }
        }
    }
}
