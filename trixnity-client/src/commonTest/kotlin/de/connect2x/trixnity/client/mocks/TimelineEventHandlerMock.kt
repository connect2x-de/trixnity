package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.room.TimelineEventHandler
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.flow.MutableStateFlow

class TimelineEventHandlerMock : TimelineEventHandler {
    val unsafeFillTimelineGaps = MutableStateFlow(false)

    override suspend fun unsafeFillTimelineGaps(startEventId: EventId, roomId: RoomId, limit: Long): Result<Unit> {
        unsafeFillTimelineGaps.value = true
        return Result.success(Unit)
    }
}
