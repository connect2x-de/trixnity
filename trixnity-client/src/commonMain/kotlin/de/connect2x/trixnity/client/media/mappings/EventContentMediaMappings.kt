package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.EventContent

data class EventContentMediaMappings(val mappings: List<EventContentMediaMapping<*>>) {
    companion object
}

@Suppress("UNCHECKED_CAST")
internal suspend fun EventContentMediaMappings.findAndCallUploaderOrFallback(
    onUploadProgress: (FileTransferProgress?) -> Unit,
    content: EventContent,
    upload: suspend (cacheUri: String, onProgress: (FileTransferProgress) -> Unit) -> String,
): EventContent {
    val uploader =
        mappings.find { it.kClass.isInstance(content) && it.uploader != null }?.uploader
            as? EventContentMediaUploader<EventContent> ?: getFallbackEventContentMediaUploader()

    return uploader(onUploadProgress, content, upload)
}

@Suppress("UNCHECKED_CAST")
internal suspend fun EventContentMediaMappings.findAndCallUriExtractorOrFallback(content: EventContent): Set<String> =
    (mappings.find { it.kClass.isInstance(content) && it.uriExtractor != null }?.uriExtractor
        as? EventContentUriExtractor<EventContent> ?: getFallbackEventContentUriExtractor())(content)
