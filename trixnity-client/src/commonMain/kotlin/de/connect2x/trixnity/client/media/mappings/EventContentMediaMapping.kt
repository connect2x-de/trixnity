package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.core.model.events.EventContent
import kotlin.reflect.KClass

data class EventContentMediaMapping<T : EventContent>(
    val kClass: KClass<T>,
    val uploader: EventContentMediaUploader<T>?,
    val uriExtractor: EventContentUriExtractor<T>?
) {
    companion object {
        inline fun <reified C : EventContent> of(
            uploader: EventContentMediaUploader<C>?,
            uriExtractor: EventContentUriExtractor<C>?
        ): EventContentMediaMapping<C> {
            return EventContentMediaMapping(C::class, uploader, uriExtractor)
        }
    }
}
