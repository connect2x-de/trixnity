package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EphemeralEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mtyping">matrix spec</a> */
@Serializable data class TypingEventContent(@SerialName("user_ids") val users: Set<UserId>) : EphemeralEventContent
