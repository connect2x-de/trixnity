package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent

fun MessageBuilder.redact(
    event: TimelineEvent,
    reason: String? = null,
) = redact(event.eventId, reason)

fun MessageBuilder.redact(
    event: MessageEvent<*>,
    reason: String? = null,
) = redact(event.id, reason)

fun MessageBuilder.redact(
    eventId: EventId,
    reason: String? = null,
) {
    content(RedactionEventContent(eventId, reason))
}
