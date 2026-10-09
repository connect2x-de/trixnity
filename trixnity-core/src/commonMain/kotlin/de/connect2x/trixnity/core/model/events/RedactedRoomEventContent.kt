package de.connect2x.trixnity.core.model.events

import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo

@Deprecated("use RedactedRoomEventContent instead", ReplaceWith("RedactedRoomEventContent"))
typealias RedactedEventContent = RedactedRoomEventContent

sealed interface RedactedRoomEventContent : RoomEventContent {
    val eventType: String
}

interface RedactedMessageEventContent : RedactedRoomEventContent, MessageEventContent

interface RedactedStateEventContent : RedactedRoomEventContent, StateEventContent

data class RedactedMessageEventContentImpl(override val eventType: String) : RedactedMessageEventContent {
    // TODO serialize when MSC3389 is in spec
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    override fun copyWith(relatesTo: RelatesTo?) = this
}

@OptIn(MSC4354::class)
data class RedactedStickyEventContentImpl(override val eventType: String, override val stickyKey: String?) :
    RedactedMessageEventContent, StickyEventContent {
    // TODO serialize when MSC3389 is in spec
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    override fun copyWith(relatesTo: RelatesTo?) = this
}

data class RedactedStateEventContentImpl(override val eventType: String) : RedactedStateEventContent {
    override val externalUrl: String? = null
}
