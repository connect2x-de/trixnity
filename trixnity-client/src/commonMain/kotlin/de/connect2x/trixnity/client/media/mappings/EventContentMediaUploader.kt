package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.EventContent

interface EventContentMediaUploader<T : EventContent> {
    suspend operator fun invoke(
        onUploadProgress: (FileTransferProgress?) -> Unit,
        content: T,
        upload: suspend (cacheUri: String, onProgress: (FileTransferProgress) -> Unit) -> String,
    ): T
}
