package de.connect2x.trixnity.core.model.events.block.m

import de.connect2x.trixnity.core.MSC3644
import de.connect2x.trixnity.core.model.events.block.EventContentBlock
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@MSC3644
@Serializable
@JvmInline
value class RelatesToContentBlock(val relatesTo: RelatesTo) : EventContentBlock.Mixin {
    override val type: EventContentBlock.Type<RelatesToContentBlock>
        get() = Type

    companion object Type : EventContentBlock.Type<RelatesToContentBlock> {
        override val value: String = "m.relates_to"

        override fun toString(): String = value
    }
}
