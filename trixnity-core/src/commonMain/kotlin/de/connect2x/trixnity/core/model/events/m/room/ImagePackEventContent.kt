package de.connect2x.trixnity.core.model.events.m.room

import de.connect2x.trixnity.core.model.events.StateEventContent
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** @see <a href="https://spec.matrix.org/latest/client-server-api/#mroomimage_pack">matrix spec</a> */
@Serializable
data class ImagePackEventContent(
    @SerialName("images") val images: Map<String, ImagePackImage>,
    @SerialName("pack") val pack: ImagePackMeta? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable
    data class ImagePackImage(
        @SerialName("url") val url: String,
        @SerialName("body") val body: String? = null,
        @SerialName("info") val info: ImageInfo? = null,
    )

    @Serializable
    data class ImagePackMeta(
        @SerialName("attribution") val attribution: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("usage") val usage: List<Usage>? = null,
    ) {
        @Serializable(with = Usage.Serializer::class)
        sealed interface Usage {
            val value: String

            object Sticker : Usage {
                override val value: String = "stickers"
            }

            object Emoticon : Usage {
                override val value: String = "emoticon"
            }

            data class Unknown(override val value: String) : Usage

            object Serializer : KSerializer<Usage> {
                @OptIn(InternalSerializationApi::class)
                override val descriptor: SerialDescriptor =
                    buildSerialDescriptor("ImagePackEventContent.Metadata.Usage", PrimitiveKind.STRING)

                override fun serialize(encoder: Encoder, value: Usage) = encoder.encodeString(value.value)

                override fun deserialize(decoder: Decoder): Usage =
                    when (val usageString = decoder.decodeString()) {
                        Sticker.value -> Sticker
                        Emoticon.value -> Emoticon
                        else -> Unknown(usageString)
                    }
            }
        }
    }
}
