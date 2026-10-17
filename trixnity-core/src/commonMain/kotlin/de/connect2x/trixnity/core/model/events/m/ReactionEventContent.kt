package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.MSC4027
import de.connect2x.trixnity.core.model.events.MessageEventContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames

@Serializable
data class ReactionEventContent(
    @SerialName("m.relates_to") override val relatesTo: RelatesTo.Annotation? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
    @MSC4027
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("shortcode")
    @SerialName("com.beeper.reaction.shortcode")
    val shortcode: String? = null,
) : MessageEventContent {
    @Transient override val mentions: Mentions? = null

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo as? RelatesTo.Annotation)
}
