package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.core.model.events.EventContent

data class EventContentMediaMappings(val mappings: List<EventContentMediaMapping<*>>) {
    companion object
}

@Suppress("UNCHECKED_CAST")
fun <T : EventContent> EventContentMediaMappings.findUploaderOrFallback(content: T): EventContentMediaUploader<T> =
    mappings.find { it.kClass.isInstance(content) && it.uploader != null }?.uploader as? EventContentMediaUploader<T>
        ?: getFallbackEventContentMediaUploader()

@Suppress("UNCHECKED_CAST")
fun <T : EventContent> EventContentMediaMappings.findUriExtractorOrFallback(content: T): EventContentUriExtractor<T> =
    mappings.find { it.kClass.isInstance(content) && it.uriExtractor != null }?.uriExtractor as? EventContentUriExtractor<T>
        ?: getFallbackEventContentUriExtractor()

