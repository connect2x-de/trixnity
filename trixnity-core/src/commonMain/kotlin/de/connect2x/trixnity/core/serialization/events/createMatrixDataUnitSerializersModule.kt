package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

fun createMatrixDataUnitSerializersModule(
    mappings: EventContentSerializerMappings,
    roomVersionStore: RoomVersionStore,
): SerializersModule {
    val ephemeralDataUnitSerializer = EphemeralDataUnitSerializer(mappings.ephemeralDataUnit)
    val persistentMessageDataUnitSerializer = PersistentMessageDataUnitSerializer(mappings.message, roomVersionStore)
    val persistentStateDataUnitSerializer = PersistentStateDataUnitSerializer(mappings.state, roomVersionStore)
    val persistentDataUnitSerializer =
        PersistentDataUnitSerializer(persistentMessageDataUnitSerializer, persistentStateDataUnitSerializer)
    val eventContentBlocksSerializer = EventContentBlocks.Serializer(mappings.block)
    val eventTypeSerializer = EventTypeSerializer(mappings)
    return SerializersModule {
        contextual(ephemeralDataUnitSerializer)
        contextual(persistentMessageDataUnitSerializer)
        contextual(persistentStateDataUnitSerializer)
        contextual(persistentDataUnitSerializer)
        contextual(eventContentBlocksSerializer)
        contextual(eventTypeSerializer)
    }
}
