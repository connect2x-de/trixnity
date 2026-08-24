package de.connect2x.trixnity.core.model.events.block

import kotlin.jvm.JvmInline
import kotlinx.serialization.json.JsonElement

sealed interface EventContentBlock {
    val type: Type<*>

    interface Type<T : EventContentBlock> {
        val value: String
    }

    interface Default : EventContentBlock

    interface Mixin : EventContentBlock

    data class Unknown(private val rawType: String, val raw: JsonElement) : EventContentBlock {
        @JvmInline value class Type(override val value: String) : EventContentBlock.Type<Unknown>

        override val type: Type = Type(rawType)
    }
}
