package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class DirectEventContent(val mappings: Map<UserId, Set<RoomId>?>) : GlobalAccountDataEventContent
