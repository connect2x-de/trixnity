package de.connect2x.trixnity.client.room.outbox


import de.connect2x.trixnity.client.media.mappings.EventContentUriExtractor
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.coroutines.coroutineScope

class FileMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.File> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.File,
    ): Set<String> = coroutineScope {
        setOfOrEmpty(content.getContentUri())
    }
}

class ImageMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.Image> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.Image,
    ): Set<String> = coroutineScope {
        setOfOrEmpty(content.getContentUri()) + setOfOrEmpty(content.info?.thumbnailUrl)
    }
}

class VideoMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.Video> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.Video,
    ): Set<String> = coroutineScope {
        setOfOrEmpty(content.getContentUri()) + setOfOrEmpty(content.info?.thumbnailUrl)
    }
}

class AudioMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased.Audio> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased.Audio,
    ): Set<String> = coroutineScope {
        setOfOrEmpty(content.getContentUri())
    }
}

// setOfNotNull is too much unnecessary overhead
private fun <T : Any> setOfOrEmpty(of: T?): Set<T> = of?.let { setOf(it) } ?: emptySet()

private fun RoomMessageEventContent.FileBased.getContentUri(): String? = this.file?.url ?: this.url

