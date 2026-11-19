package de.connect2x.trixnity.client.room.outbox

import de.connect2x.trixnity.client.media.mappings.EventContentMediaUploader
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent

class FileMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.File> {
    override suspend fun invoke(
        onUploadProgress: (FileTransferProgress?) -> Unit,
        content: RoomMessageEventContent.FileBased.File,
        upload: suspend (String, (FileTransferProgress) -> Unit) -> String,
    ): RoomMessageEventContent.FileBased.File {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress(onUploadProgress)
        val thumbnailUploadProgress =
            combinedUploadProgress.acquire(
                if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null)
                    FileTransferProgress(0, content.info?.thumbnailInfo?.size)
                else null
            )
        val fileUploadProgress = combinedUploadProgress.acquire(FileTransferProgress(0, content.info?.size))

        combinedUploadProgress.publish()
        return if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it, thumbnailUploadProgress) }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info =
                    content.info?.copy(
                        thumbnailFile = thumbnailMxcUri?.let { content.info?.thumbnailFile?.copy(url = it) }
                    ),
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it, thumbnailUploadProgress) }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            content.copy(url = mxcUri, info = content.info?.copy(thumbnailUrl = thumbnailMxcUri))
        } else content
    }
}

class ImageMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.Image> {
    override suspend fun invoke(
        onUploadProgress: (FileTransferProgress?) -> Unit,
        content: RoomMessageEventContent.FileBased.Image,
        upload: suspend (String, (FileTransferProgress) -> Unit) -> String,
    ): RoomMessageEventContent.FileBased.Image {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress(onUploadProgress)
        val thumbnailUploadProgress =
            combinedUploadProgress.acquire(
                if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null)
                    FileTransferProgress(0, content.info?.thumbnailInfo?.size)
                else null
            )
        val fileUploadProgress = combinedUploadProgress.acquire(FileTransferProgress(0, content.info?.size))
        combinedUploadProgress.publish()
        return if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it, thumbnailUploadProgress) }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info =
                    content.info?.copy(
                        thumbnailFile = thumbnailMxcUri?.let { content.info?.thumbnailFile?.copy(url = it) }
                    ),
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it, thumbnailUploadProgress) }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            content.copy(url = mxcUri, info = content.info?.copy(thumbnailUrl = thumbnailMxcUri))
        } else content
    }
}

class VideoMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.Video> {
    override suspend fun invoke(
        onUploadProgress: (FileTransferProgress?) -> Unit,
        content: RoomMessageEventContent.FileBased.Video,
        upload: suspend (String, (FileTransferProgress) -> Unit) -> String,
    ): RoomMessageEventContent.FileBased.Video {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress(onUploadProgress)
        val thumbnailUploadProgress =
            combinedUploadProgress.acquire(
                if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null)
                    FileTransferProgress(0, content.info?.thumbnailInfo?.size)
                else null
            )
        val fileUploadProgress = combinedUploadProgress.acquire(FileTransferProgress(0, content.info?.size))
        combinedUploadProgress.publish()
        return if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it, thumbnailUploadProgress) }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info =
                    content.info?.copy(
                        thumbnailFile = thumbnailMxcUri?.let { content.info?.thumbnailFile?.copy(url = it) }
                    ),
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it, thumbnailUploadProgress) }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            content.copy(url = mxcUri, info = content.info?.copy(thumbnailUrl = thumbnailMxcUri))
        } else content
    }
}

class AudioMessageEventContentMediaUploader() : EventContentMediaUploader<RoomMessageEventContent.FileBased.Audio> {
    override suspend fun invoke(
        onUploadProgress: (FileTransferProgress?) -> Unit,
        content: RoomMessageEventContent.FileBased.Audio,
        upload: suspend (String, (FileTransferProgress) -> Unit) -> String,
    ): RoomMessageEventContent.FileBased.Audio {
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        return if (encryptedContentUrl != null) {
            val mxcUri = upload(encryptedContentUrl, onUploadProgress)
            content.copy(file = content.file?.copy(url = mxcUri))
        } else if (contentUrl != null) {
            val mxcUri = upload(contentUrl, onUploadProgress)
            content.copy(url = mxcUri)
        } else content
    }
}
