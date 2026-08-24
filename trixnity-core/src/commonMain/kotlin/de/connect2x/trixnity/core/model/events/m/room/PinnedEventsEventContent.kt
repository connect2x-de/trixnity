package de.connect2x.trixnity.core.model.events.m.room

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.StateEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroompinned_events">matrix spec</a> */
@Serializable
data class PinnedEventsEventContent(
    @SerialName("pinned") val pinned: List<EventId> = listOf(),
    @SerialName("external_url") override val externalUrl: String? = null,
) : StateEventContent
