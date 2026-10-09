package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.EphemeralDataUnitContent
import de.connect2x.trixnity.core.model.events.EphemeralEventContent
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RedactedMessageEventContent
import de.connect2x.trixnity.core.model.events.RedactedRoomEventContent
import de.connect2x.trixnity.core.model.events.RedactedStateEventContent
import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

class UnsupportedEventContentTypeException(eventType: KClass<*>) :
    IllegalArgumentException(
        "Event content type $eventType is not supported. If it is a custom type, you should register it!"
    )

@JvmName("messageEventContentSerializerFromType")
fun Set<MessageEventContentSerializerMapping>.contentSerializer(
    type: String,
    content: MessageEventContent? = null,
): KSerializer<MessageEventContent> =
    if (content != null) contentSerializer(content)
    else
        firstOrNull { type == it.type }?.serializer
            ?: @Suppress("UNCHECKED_CAST") (UnknownEventContentSerializer(type) as KSerializer<MessageEventContent>)

@JvmName("stateEventContentSerializerFromType")
fun Set<StateEventContentSerializerMapping>.contentSerializer(
    type: String,
    content: StateEventContent? = null,
): KSerializer<StateEventContent> =
    if (content != null) contentSerializer(content)
    else
        firstOrNull { type == it.type }?.serializer
            ?: @Suppress("UNCHECKED_CAST") (UnknownEventContentSerializer(type) as KSerializer<StateEventContent>)

@JvmName("ephemeralEventContentSerializerFromType")
fun Set<EventContentSerializerMapping<EphemeralEventContent>>.contentSerializer(
    type: String,
    content: EphemeralEventContent? = null,
): KSerializer<EphemeralEventContent> =
    if (content != null) contentSerializer(content)
    else
        firstOrNull { type == it.type }?.serializer
            ?: @Suppress("UNCHECKED_CAST") (UnknownEventContentSerializer(type) as KSerializer<EphemeralEventContent>)

@JvmName("ephemeralDataUnitContentSerializerFromType")
fun Set<EventContentSerializerMapping<EphemeralDataUnitContent>>.contentSerializer(
    type: String,
    content: EphemeralDataUnitContent? = null,
): KSerializer<EphemeralDataUnitContent> =
    if (content != null) contentSerializer(content)
    else
        firstOrNull { type == it.type }?.serializer
            ?: @Suppress("UNCHECKED_CAST")
            (UnknownEventContentSerializer(type) as KSerializer<EphemeralDataUnitContent>)

@JvmName("toDeviceEventContentSerializerFromType")
fun Set<EventContentSerializerMapping<ToDeviceEventContent>>.contentSerializer(
    type: String,
    content: ToDeviceEventContent? = null,
): KSerializer<ToDeviceEventContent> =
    if (content != null) contentSerializer(content)
    else
        firstOrNull { type == it.type }?.serializer
            ?: @Suppress("UNCHECKED_CAST") (UnknownEventContentSerializer(type) as KSerializer<ToDeviceEventContent>)

@JvmName("roomAccountDataEventContentSerializerFromType")
fun Set<EventContentSerializerMapping<RoomAccountDataEventContent>>.contentSerializer(
    type: String,
    content: RoomAccountDataEventContent? = null,
): KSerializer<RoomAccountDataEventContent> =
    if (content != null) contentSerializer(content)
    else
        firstOrNull { type.startsWith(it.type) }?.serializer
            ?: @Suppress("UNCHECKED_CAST")
            (UnknownEventContentSerializer(type) as KSerializer<RoomAccountDataEventContent>)

@JvmName("globalAccountDataEventContentSerializerFromType")
fun Set<EventContentSerializerMapping<GlobalAccountDataEventContent>>.contentSerializer(
    type: String,
    content: GlobalAccountDataEventContent? = null,
): KSerializer<GlobalAccountDataEventContent> =
    if (content != null) contentSerializer(content)
    else
        firstOrNull { type.startsWith(it.type.removeSuffix("*")) }?.serializer
            ?: @Suppress("UNCHECKED_CAST")
            (UnknownEventContentSerializer(type) as KSerializer<GlobalAccountDataEventContent>)

@JvmName("contentSerializerFromContent")
fun <T : EventContent> Set<EventContentSerializerMapping<T>>.contentSerializer(content: T): KSerializer<T> =
    when (content) {
        is UnknownEventContent ->
            @Suppress("UNCHECKED_CAST") (UnknownEventContentSerializer(content.eventType) as KSerializer<T>)
        is RedactedMessageEventContent ->
            @Suppress("UNCHECKED_CAST") (RedactedMessageEventContentSerializer(content.eventType) as KSerializer<T>)
        is RedactedStateEventContent ->
            @Suppress("UNCHECKED_CAST") (RedactedStateEventContentSerializer(content.eventType) as KSerializer<T>)

        else ->
            find { it.kClass.isInstance(content) }?.serializer
                ?: throw UnsupportedEventContentTypeException(content::class)
    }

fun Set<EventContentSerializerMapping<*>>.contentType(content: EventContent): String =
    when (content) {
        is UnknownEventContent -> content.eventType
        is RedactedRoomEventContent -> content.eventType
        else ->
            find { it.kClass.isInstance(content) }?.type ?: throw UnsupportedEventContentTypeException(content::class)
    }

fun Set<EventContentSerializerMapping<*>>.contentType(eventContentClass: KClass<out EventContent>): String =
    firstOrNull { it.kClass == eventContentClass }?.type
        ?: throw UnsupportedEventContentTypeException(eventContentClass)
