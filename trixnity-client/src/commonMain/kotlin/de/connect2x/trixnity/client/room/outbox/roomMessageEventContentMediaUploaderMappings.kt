package de.connect2x.trixnity.client.room.outbox

import de.connect2x.trixnity.client.media.mappings.EventContentMediaUploader
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

//Suppressing unchecked casts here is fine, as the data classes are final
@Suppress("UNCHECKED_CAST")
class FileMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.File> {
    override suspend fun <S : RoomMessageEventContent.FileBased.File> invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: S,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): S = coroutineScope {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress()
        val thumbnailUploadProgress = combinedUploadProgress.acquire()
        thumbnailUploadProgress.value =
            if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null) FileTransferProgress(
                0,
                content.info?.thumbnailInfo?.size
            ) else null
        val fileUploadProgress = combinedUploadProgress.acquire()
        fileUploadProgress.value = FileTransferProgress(0, content.info?.size)
        val updateProgress = launch {
            combinedUploadProgress.collect(uploadProgress)
        }

        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            ) as S
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            ) as S
        } else content
    }

}

@Suppress("UNCHECKED_CAST")
class ImageMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.Image> {
    override suspend fun <S : RoomMessageEventContent.FileBased.Image> invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: S,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): S = coroutineScope {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress()
        val updateProgress = launch {
            combinedUploadProgress.collect(uploadProgress)
        }
        val thumbnailUploadProgress = combinedUploadProgress.acquire()
        thumbnailUploadProgress.value =
            if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null) FileTransferProgress(
                0,
                content.info?.thumbnailInfo?.size
            ) else null
        val fileUploadProgress = combinedUploadProgress.acquire()
        fileUploadProgress.value = FileTransferProgress(0, content.info?.size)
        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            ) as S
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            ) as S
        } else content
    }
}

@Suppress("UNCHECKED_CAST")
class VideoMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.Video> {
    override suspend fun <S : RoomMessageEventContent.FileBased.Video> invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: S,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): S = coroutineScope {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress()
        val thumbnailUploadProgress = combinedUploadProgress.acquire()
        thumbnailUploadProgress.value =
            if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null) FileTransferProgress(
                0,
                content.info?.thumbnailInfo?.size
            ) else null
        val fileUploadProgress = combinedUploadProgress.acquire()
        fileUploadProgress.value = FileTransferProgress(0, content.info?.size)
        val updateProgress = launch {
            combinedUploadProgress.collect(uploadProgress)
        }
        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            ) as S
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            ) as S
        } else content
    }

}

@Suppress("UNCHECKED_CAST")
class AudioMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.Audio> {
    override suspend fun <S : RoomMessageEventContent.FileBased.Audio> invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: S,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): S {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        return if (encryptedContentUrl != null) {
            val mxcUri = upload(encryptedContentUrl, uploadProgress)
            content.copy(file = content.file?.copy(url = mxcUri)) as S
        } else if (contentUrl != null) {
            val mxcUri = upload(contentUrl, uploadProgress)
            content.copy(url = mxcUri) as S
        } else content
    }
}
