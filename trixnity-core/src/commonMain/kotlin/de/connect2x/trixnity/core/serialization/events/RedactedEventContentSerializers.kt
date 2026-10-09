package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.RedactedMessageEventContent
import de.connect2x.trixnity.core.model.events.RedactedMessageEventContentImpl
import de.connect2x.trixnity.core.model.events.RedactedStateEventContent
import de.connect2x.trixnity.core.model.events.RedactedStateEventContentImpl
import de.connect2x.trixnity.core.model.events.RedactedStickyEventContentImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class RedactedMessageEventContentSerializer(val eventType: String) : KSerializer<RedactedMessageEventContent> {
    override val descriptor = buildClassSerialDescriptor("RedactedMessageEventContent")

    override fun deserialize(decoder: Decoder): RedactedMessageEventContent {
        require(decoder is JsonDecoder)
        val stickyKey = ((decoder.decodeJsonElement() as? JsonObject)?.get("sticky_key") as? JsonPrimitive)?.content
        return if (stickyKey != null) RedactedStickyEventContentImpl(eventType, stickyKey)
        else RedactedMessageEventContentImpl(eventType)
    }

    override fun serialize(encoder: Encoder, value: RedactedMessageEventContent) {
        require(encoder is JsonEncoder)
        if (value is RedactedStickyEventContentImpl)
            encoder.encodeJsonElement(buildJsonObject { "sticky_key" to JsonPrimitive(value.stickyKey) })
        else encoder.encodeJsonElement(JsonObject(mapOf()))
    }
}

class RedactedStateEventContentSerializer(val eventType: String) : KSerializer<RedactedStateEventContent> {
    override val descriptor = buildClassSerialDescriptor("RedactedStateEventContent")

    override fun deserialize(decoder: Decoder): RedactedStateEventContent {
        require(decoder is JsonDecoder)
        return RedactedStateEventContentImpl(eventType)
    }

    override fun serialize(encoder: Encoder, value: RedactedStateEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonObject(mapOf()))
    }
}
