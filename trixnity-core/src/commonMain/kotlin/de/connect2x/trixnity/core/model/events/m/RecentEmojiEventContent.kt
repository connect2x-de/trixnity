package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecentEmojiEventContent(
    @SerialName("recent_emoji")
    val recentEmoji: List<RecentEmoji>
) : GlobalAccountDataEventContent {
    @Serializable
    data class RecentEmoji(
        val emoji: String,
        val total: Long,
    )
}
