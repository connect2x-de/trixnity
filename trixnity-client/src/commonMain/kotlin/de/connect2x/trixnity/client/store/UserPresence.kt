package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.events.m.Presence
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserPresence(
    val presence: Presence,
    /** The instant, when the last update of the [UserPresence] arrived from sync. */
    val lastUpdate: Instant,
    /** The instant, when the server marked the user as active. */
    val lastActive: Instant? = null,
    val isCurrentlyActive: Boolean? = null,
    val statusMessage: String? = null,
)
