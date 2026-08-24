package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent

class DecryptedOlmEventSerializer(eventContentSerializers: Set<EventContentSerializerMapping<EventContent>>) :
    BaseEventSerializer<EventContent, PlaintextOlmEvent<*>>(
        "DecryptedOlmEvent",
        EventContentToEventSerializerMappings(
            baseMapping = eventContentSerializers,
            eventDeserializer = { PlaintextOlmEvent.serializer(it.serializer) },
            unknownEventSerializer = { PlaintextOlmEvent.serializer(UnknownEventContentSerializer(it)) },
        ),
    )
