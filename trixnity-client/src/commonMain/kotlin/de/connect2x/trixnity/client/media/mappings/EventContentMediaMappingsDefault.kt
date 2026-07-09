package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.client.media.mappings.EventContentMediaMapping.Companion.of
import de.connect2x.trixnity.client.room.outbox.AudioMessageEventContentMediaUploader
import de.connect2x.trixnity.client.room.outbox.AudioMessageEventContentUriExtractor
import de.connect2x.trixnity.client.room.outbox.FileMessageEventContentMediaUploader
import de.connect2x.trixnity.client.room.outbox.FileMessageEventContentUriExtractor
import de.connect2x.trixnity.client.room.outbox.ImageMessageEventContentMediaUploader
import de.connect2x.trixnity.client.room.outbox.ImageMessageEventContentUriExtractor
import de.connect2x.trixnity.client.room.outbox.VideoMessageEventContentMediaUploader
import de.connect2x.trixnity.client.room.outbox.VideoMessageEventContentUriExtractor
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent

private val eventContentMediaMappingsDefault = EventContentMediaMappings(
    listOf(
        of<RoomMessageEventContent.FileBased.File>(
            FileMessageEventContentMediaUploader(),
            FileMessageEventContentUriExtractor()
        ),
        of<RoomMessageEventContent.FileBased.Image>(
            ImageMessageEventContentMediaUploader(),
            ImageMessageEventContentUriExtractor()
        ),
        of<RoomMessageEventContent.FileBased.Video>(
            VideoMessageEventContentMediaUploader(),
            VideoMessageEventContentUriExtractor()
        ),
        of<RoomMessageEventContent.FileBased.Audio>(
            AudioMessageEventContentMediaUploader(),
            AudioMessageEventContentUriExtractor()
        )
    )
)

val EventContentMediaMappings.Companion.default get() = eventContentMediaMappingsDefault
