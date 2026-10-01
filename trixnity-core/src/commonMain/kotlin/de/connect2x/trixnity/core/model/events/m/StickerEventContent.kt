package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.ImageInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.19/client-server-api/#msticker">matrix spec</a> */
@Serializable
data class StickerEventContent(
    @SerialName("url") val url: String,
    @SerialName("body") val body: String,
    @SerialName("info") val info: ImageInfo,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : MessageEventContent {
    override fun copyWith(relatesTo: RelatesTo?): MessageEventContent = copy(relatesTo = relatesTo)
}
