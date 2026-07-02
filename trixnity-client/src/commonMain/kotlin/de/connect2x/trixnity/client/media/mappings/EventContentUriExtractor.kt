package de.connect2x.trixnity.client.media.mappings

import de.connect2x.trixnity.core.model.events.EventContent

interface EventContentUriExtractor<T : EventContent> {
    suspend operator fun invoke(
        content: T
    ): String?
}
