package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.CacheTransaction
import de.connect2x.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import de.connect2x.trixnity.client.store.cache.MinimalDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.TimelineEventKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationRepository
import de.connect2x.trixnity.client.store.repository.TimelineEventRepository
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class RoomTimelineStore(
    timelineEventRepository: TimelineEventRepository,
    timelineEventRelationRepository: TimelineEventRelationRepository,
    tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val timelineEventCache = MinimalDeleteByRoomIdRepositoryObservableCache(
        timelineEventRepository,
        tm,
        storeScope,
        clock,
        config.cacheExpireDurations.timelineEvent
    ) { it.roomId }.also(statisticCollector::addCache)
    private val timelineEventRelationCache =
        MapDeleteByRoomIdRepositoryObservableCache(
            timelineEventRelationRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.timelineEventRelation
        ) { it.firstKey.roomId }.also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        timelineEventCache.deleteAll()
        timelineEventRelationCache.deleteAll()
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteByRoomId(roomId: RoomId) {
        timelineEventCache.deleteByRoomId(roomId)
        timelineEventRelationCache.deleteByRoomId(roomId)
    }

    fun get(eventId: EventId, roomId: RoomId): Flow<TimelineEvent?> =
        timelineEventCache.get(TimelineEventKey(eventId, roomId))

    context(transaction: StoreWriteTransaction)
    suspend fun update(
        eventId: EventId,
        roomId: RoomId,
        updater: (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = timelineEventCache.update(
        TimelineEventKey(eventId, roomId),
        updater = updater
    )

    context(transaction: CacheTransaction)
    suspend fun updateCacheOnly(
        eventId: EventId,
        roomId: RoomId,
        updater: (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = timelineEventCache.updateCacheOnly(
        TimelineEventKey(eventId, roomId),
        updater = updater
    )

    context(transaction: StoreWriteTransaction)
    suspend fun add(timelineEvent: TimelineEvent) {
        timelineEventCache.set(TimelineEventKey(timelineEvent.eventId, timelineEvent.roomId), timelineEvent)
    }

    fun getRelations(
        relatedEventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ): Flow<Map<EventId, Flow<TimelineEventRelation?>>> =
        timelineEventRelationCache.readByFirstKey(
            TimelineEventRelationKey(relatedEventId, roomId, relationType)
        )

    context(transaction: StoreWriteTransaction)
    suspend fun addRelation(relation: TimelineEventRelation) {
        timelineEventRelationCache.set(
            MapRepositoryCoroutinesCacheKey(
                TimelineEventRelationKey(relation.relatedEventId, relation.roomId, relation.relationType),
                relation.eventId
            ), relation
        )
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteRelation(relation: TimelineEventRelation) {
        timelineEventRelationCache.set(
            MapRepositoryCoroutinesCacheKey(
                TimelineEventRelationKey(relation.relatedEventId, relation.roomId, relation.relationType),
                relation.eventId
            ), null
        )
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteRelations(
        relatedEventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ) {
        timelineEventRelationCache.readByFirstKey(TimelineEventRelationKey(relatedEventId, roomId, relationType))
            .first()
            .values
            .mapNotNull { it.first() }
            .forEach { deleteRelation(it) }
    }
}
