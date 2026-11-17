package de.connect2x.trixnity.client

import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappingsBuilder

/**
 * Use this to register custom event types.
 *
 * Example:
 * ```kotlin
 * module {
 *   single<CustomEventContentSerializerMappings>(named("MyCustomEventContentSerializerMappings")) {
 *       CustomEventContentSerializerMappings {
 *           blockOf(DinoContentBlock)
 *           messageOf<DinoEventContent>("de.connect2x.dino")
 *       }
 *   }
 * }
 * ```
 */
interface CustomEventContentSerializerMappings : EventContentSerializerMappings {
    companion object {
        operator fun invoke(
            builder: EventContentSerializerMappingsBuilder.() -> Unit
        ): CustomEventContentSerializerMappings =
            object :
                CustomEventContentSerializerMappings,
                EventContentSerializerMappings by EventContentSerializerMappingsBuilder().apply(builder).build() {}
    }
}
