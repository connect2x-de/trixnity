package de.connect2x.trixnity.client.media.mappings

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.EventContent
import kotlinx.coroutines.flow.MutableStateFlow

private val log = Logger("de.connect2x.trixnity.client.media.mappings.FallbackOutboxMessageMediaUploaderMapping")


interface FallBackEventContentMediaUploader : EventContentMediaUploader<EventContent>

internal fun getFallbackEventContentMediaUploader(): FallBackEventContentMediaUploader {
    return object : FallBackEventContentMediaUploader {
        override suspend fun invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: EventContent,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): EventContent {
            log.trace {
                "EventContent class ${content::class.simpleName} is not supported by any other media uploader."
            }
            return content
        }
    }
}

fun interface FallBackEventContentUriExtractor : EventContentUriExtractor<EventContent>

internal fun getFallbackEventContentUriExtractor(): FallBackEventContentUriExtractor {
    return FallBackEventContentUriExtractor { content ->
        log.trace {
            "EventContent class ${content::class.simpleName} is not supported by any other media URI extractor."
        }
        setOf()
    }
}
