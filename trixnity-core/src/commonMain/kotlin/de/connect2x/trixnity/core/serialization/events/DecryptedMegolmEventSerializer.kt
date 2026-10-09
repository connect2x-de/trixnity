package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.PlaintextMegolmEvent

class DecryptedMegolmEventSerializer(
    messageEventContentSerializers: Set<EventContentSerializerMapping<MessageEventContent>>
) :
    BaseEventSerializer<MessageEventContent, PlaintextMegolmEvent<*>>(
        "DecryptedMegolmEvent",
        RoomEventContentToEventSerializerMappings(
            baseMapping = messageEventContentSerializers,
            eventDeserializer = { PlaintextMegolmEvent.serializer(it.serializer) },
            unknownEventSerializer = { PlaintextMegolmEvent.serializer(UnknownEventContentSerializer(it)) },
            redactedEventSerializer = { PlaintextMegolmEvent.serializer(RedactedMessageEventContentSerializer(it)) },
        ),
    )
