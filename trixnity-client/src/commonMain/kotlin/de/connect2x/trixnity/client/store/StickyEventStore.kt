package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.FullMapDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive

@MSC4354
class StickyEventStore(
    private val stickyEventRepository: StickyEventRepository,
    tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    private val clock: Clock,
) : Store {

    private val stickyEventCache =
        FullMapDeleteByRoomIdRepositoryObservableCache(
                stickyEventRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.stickyEvent,
                valueToKeyMapper = { MapRepositoryCoroutinesCacheKey(it.roomId, it.eventId) },
            ) {
                it.firstKey
            }
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        stickyEventCache.deleteAll()
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteByRoomId(roomId: RoomId) {
        stickyEventCache.deleteByRoomId(roomId)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteByEventId(roomId: RoomId, eventId: EventId) {
        stickyEventCache.set(MapRepositoryCoroutinesCacheKey(roomId, eventId), null)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun save(storedStickyEvent: StoredStickyEvent) {
        stickyEventCache.set(
            MapRepositoryCoroutinesCacheKey(storedStickyEvent.roomId, storedStickyEvent.eventId),
            storedStickyEvent,
        )
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteInvalid() {
        val now = clock.now()
        val invalidEvents = stickyEventRepository.getByEndTimeBefore(now)
        for ((roomId, eventId) in invalidEvents) {
            stickyEventCache.set(MapRepositoryCoroutinesCacheKey(roomId, eventId), null)
        }
    }

    fun getAll(): Flow<Map<Pair<RoomId, EventId>, Flow<StoredStickyEvent?>>> =
        stickyEventCache.getAll().map { map ->
            map.mapKeys { (key, _) -> key.firstKey to key.secondKey }.mapValues { (_, value) -> value.filterValid() }
        }

    fun get(roomId: RoomId, eventId: EventId): Flow<StoredStickyEvent?> =
        stickyEventCache.get(MapRepositoryCoroutinesCacheKey(roomId, eventId)).filterValid()

    fun get(roomId: RoomId): Flow<Map<EventId, Flow<StoredStickyEvent?>>> =
        stickyEventCache.getByFirstKey(roomId).map { map -> map.mapValues { (_, value) -> value.filterValid() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<StoredStickyEvent?>.filterValid(): Flow<StoredStickyEvent?> = transformLatest {
        if (it == null || it.endTime < clock.now()) {
            emit(null)
            return@transformLatest
        }
        emit(it)
        while (currentCoroutineContext().isActive) {
            val durationToEndTime = it.endTime - clock.now()
            if (durationToEndTime <= Duration.ZERO) {
                emit(null)
                break
            }
            delay(durationToEndTime.coerceAtMost(1.seconds)) // to catch sleeping scheduler cases
            if (it.endTime < clock.now()) {
                emit(null)
                break
            }
        }
    }
}
