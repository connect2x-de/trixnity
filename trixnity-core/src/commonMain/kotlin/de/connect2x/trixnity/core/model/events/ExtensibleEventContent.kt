package de.connect2x.trixnity.core.model.events

import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import kotlinx.serialization.Serializable

/** This is based on MSC1767 and maybe included into [EventContent] in the future. */
interface ExtensibleEventContent<L : ExtensibleEventContent.Legacy> {
    val blocks: EventContentBlocks
    val legacy: L?

    interface Legacy {
        @Serializable object None : Legacy
    }
}
