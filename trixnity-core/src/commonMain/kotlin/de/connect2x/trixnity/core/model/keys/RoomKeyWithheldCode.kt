package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RoomKeyWithheldCode.Serializer::class)
sealed interface RoomKeyWithheldCode {
    val value: String

    data object Blacklisted : RoomKeyWithheldCode {
        override val value = "m.blacklisted"
    }

    data object Unverified : RoomKeyWithheldCode {
        override val value = "m.unverified"
    }

    data object Unauthorised : RoomKeyWithheldCode {
        override val value = "m.unauthorised"
    }

    data object Unavailable : RoomKeyWithheldCode {
        override val value = "m.unavailable"
    }

    data object NoOlm : RoomKeyWithheldCode {
        override val value = "m.no_olm"
    }

    data object HistoryNotShared : RoomKeyWithheldCode {
        override val value = "m.history_not_shared"
    }

    data class Unknown(override val value: String) : RoomKeyWithheldCode

    object Serializer : KSerializer<RoomKeyWithheldCode> {
        override val descriptor = PrimitiveSerialDescriptor("RoomKeyWithheldCode", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): RoomKeyWithheldCode {
            val value = decoder.decodeString()
            return when (value) {
                Blacklisted.value -> Blacklisted
                Unverified.value -> Unverified
                Unauthorised.value -> Unauthorised
                Unavailable.value -> Unavailable
                NoOlm.value -> NoOlm
                HistoryNotShared.value -> HistoryNotShared
                else -> Unknown(value)
            }
        }

        override fun serialize(encoder: Encoder, value: RoomKeyWithheldCode) {
            encoder.encodeString(value.value)
        }
    }
}
