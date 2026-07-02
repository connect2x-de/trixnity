package de.connect2x.trixnity.client.media.mappings

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.EventContent
import kotlinx.coroutines.flow.MutableStateFlow

private val log = Logger("de.connect2x.trixnity.client.media.mappings.FallbackOutboxMessageMediaUploaderMapping")


interface FallBackEventContentMediaUploader<T : EventContent> : EventContentMediaUploader<T>

internal fun <T : EventContent> getFallbackEventContentMediaUploader(): FallBackEventContentMediaUploader<T> {
    return object : FallBackEventContentMediaUploader<T> {
        override suspend fun <S : T> invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: S,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): S {
            log.trace {
                "EventContent class ${content::class.simpleName} is not supported by any other media uploader."
            }
            return content
        }
    }
}

fun interface FallBackEventContentUriExtractor<T : EventContent> : EventContentUriExtractor<T>

internal fun <T : EventContent> getFallbackEventContentUriExtractor(): FallBackEventContentUriExtractor<T> {
    return FallBackEventContentUriExtractor { content ->
        log.trace {
            "EventContent class ${content::class.simpleName} is not supported by any other media URI extractor."
        }
        null
    }
}
