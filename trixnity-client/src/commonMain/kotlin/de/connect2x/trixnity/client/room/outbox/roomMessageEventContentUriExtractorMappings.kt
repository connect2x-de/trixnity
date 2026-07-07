package de.connect2x.trixnity.client.room.outbox


import de.connect2x.trixnity.client.media.mappings.EventContentUriExtractor
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.coroutines.coroutineScope

class FileMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.File> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.File,
    ): Set<String?> = coroutineScope {
        setOf(content.file?.url ?: content.url)
    }
}

class ImageMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.Image> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.Image,
    ): Set<String?> = coroutineScope {
        setOf(content.file?.url ?: content.url, content.info?.thumbnailUrl)
    }
}

class VideoMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.Video> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.Video,
    ): Set<String?> = coroutineScope {
        setOf(content.file?.url ?: content.url, content.info?.thumbnailUrl)
    }
}

class AudioMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.Audio> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.Audio,
    ): Set<String?> = coroutineScope {
        setOf(content.file?.url ?: content.url)
    }
}

class FileBasedMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased,
    ): Set<String?> = coroutineScope {
        setOf(content.file?.url ?: content.url)
    }
}
