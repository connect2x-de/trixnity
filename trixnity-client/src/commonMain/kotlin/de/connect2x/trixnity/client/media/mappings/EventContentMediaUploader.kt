package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.EventContent
import kotlinx.coroutines.flow.MutableStateFlow

interface EventContentMediaUploader<T : EventContent> {
    suspend operator fun <S : T> invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: S,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
    ): S
}
