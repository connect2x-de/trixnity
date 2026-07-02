package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.client.media.mappings.EventContentMediaMapping.Companion.of
import de.connect2x.trixnity.client.room.outbox.AudioMessageEventContentMediaUploader
import de.connect2x.trixnity.client.room.outbox.FileBasedMessageEventContentUriExtractor
import de.connect2x.trixnity.client.room.outbox.FileMessageEventContentMediaUploader
import de.connect2x.trixnity.client.room.outbox.ImageMessageEventContentMediaUploader
import de.connect2x.trixnity.client.room.outbox.VideoMessageEventContentMediaUploader
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent

private val eventContentMediaMappingsDefault = EventContentMediaMappings(
    listOf(
        of<RoomMessageEventContent.FileBased.File>(
            FileMessageEventContentMediaUploader(),
            null
        ),
        of<RoomMessageEventContent.FileBased.Image>(
            ImageMessageEventContentMediaUploader(),
            null
        ),
        of<RoomMessageEventContent.FileBased.Video>(
            VideoMessageEventContentMediaUploader(),
            null
        ),
        of<RoomMessageEventContent.FileBased.Audio>(
            AudioMessageEventContentMediaUploader(),
            null
        ),
        of<RoomMessageEventContent.FileBased>(
            null,
            FileBasedMessageEventContentUriExtractor()
        )
    )
)

val EventContentMediaMappings.Companion.default get() = eventContentMediaMappingsDefault
