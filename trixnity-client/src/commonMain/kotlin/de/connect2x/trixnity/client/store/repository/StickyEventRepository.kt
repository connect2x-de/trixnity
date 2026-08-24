package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.utils.ReadTransaction
import kotlin.time.Instant

@MSC4354
interface StickyEventRepository :
    DeleteByRoomIdMapRepository<
        StickyEventRepositoryFirstKey,
        StickyEventRepositorySecondKey,
        StoredStickyEvent<StickyEventContent>,
    > {
    override fun serializeKey(
        firstKey: StickyEventRepositoryFirstKey,
        secondKey: StickyEventRepositorySecondKey,
    ): String = firstKey.roomId.full + firstKey.type + secondKey.sender.full + secondKey.stickyKey

    context(transaction: ReadTransaction)
    suspend fun getByEndTimeBefore(
        before: Instant
    ): Set<Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>>

    context(transaction: ReadTransaction)
    suspend fun getByEventId(
        roomId: RoomId,
        eventId: EventId,
    ): Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>?
}

@MSC4354 data class StickyEventRepositoryFirstKey(val roomId: RoomId, val type: String)

@MSC4354 data class StickyEventRepositorySecondKey(val sender: UserId, val stickyKey: String?)
