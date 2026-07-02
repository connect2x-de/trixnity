package de.connect2x.trixnity.client.room.outbox


import de.connect2x.trixnity.client.media.mappings.EventContentUriExtractor
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.coroutines.coroutineScope

class FileBasedMessageEventContentUriExtractor() : EventContentUriExtractor<RoomMessageEventContent.FileBased> {
    override suspend fun invoke(
        content: RoomMessageEventContent.FileBased,
    ): String? = coroutineScope {
        content.file?.url ?: content.url
    }
}
