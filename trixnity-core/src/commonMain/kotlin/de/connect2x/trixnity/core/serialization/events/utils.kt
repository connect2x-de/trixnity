package de.connect2x.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun <T> Json.tryDeserializeOrElse(
    serializer: KSerializer<out T>,
    jsonElement: JsonElement,
    redactedSerializer: (() -> KSerializer<out T>)? = null,
    elseSerializer: (error: Exception) -> KSerializer<out T>,
): T =
    try {
        decodeFromJsonElement(serializer, jsonElement)
    } catch (error: Exception) {
        if (
            redactedSerializer != null &&
                jsonElement is JsonObject &&
                (jsonElement.isEmpty() ||
                    jsonElement.size == 1 && jsonElement.containsKey("sticky_key") ||
                    jsonElement.size == 1 && jsonElement.containsKey("msc4354_sticky_key") ||
                    // type comes from a top-level json injection
                    jsonElement.size == 1 && jsonElement.containsKey("type") ||
                    jsonElement.size == 2 &&
                        jsonElement.containsKey("type") &&
                        (jsonElement.containsKey("sticky_key") || jsonElement.containsKey("msc4354_sticky_key")))
        )
            decodeFromJsonElement(redactedSerializer(), jsonElement)
        else decodeFromJsonElement(elseSerializer(error), jsonElement)
    }
