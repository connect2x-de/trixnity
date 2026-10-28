package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import kotlin.time.Instant

@MSC4354
interface StickyEventRepository : DeleteByRoomIdFullMapRepository<RoomId, EventId, StoredStickyEvent> {
    override fun serializeKey(firstKey: RoomId, secondKey: EventId): String = firstKey.full + secondKey.full

    context(transaction: ReadTransaction)
    suspend fun getByEndTimeBefore(before: Instant): Set<Pair<RoomId, EventId>>
}
