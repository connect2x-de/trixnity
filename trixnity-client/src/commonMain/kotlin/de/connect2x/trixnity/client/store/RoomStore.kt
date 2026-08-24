package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.FullRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RoomRepository
import de.connect2x.trixnity.core.model.RoomId
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class RoomStore(
    roomRepository: RoomRepository,
    tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomCache =
        FullRepositoryObservableCache(roomRepository, tm, storeScope, clock, config.cacheExpireDurations.room) {
                it.roomId
            }
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        roomCache.deleteAll()
    }

    fun getAll(): Flow<Map<RoomId, Flow<Room?>>> = roomCache.readAll()

    fun get(roomId: RoomId): Flow<Room?> = roomCache.get(roomId)

    context(transaction: StoreWriteTransaction)
    suspend fun update(roomId: RoomId, updater: (oldRoom: Room?) -> Room?) = roomCache.update(roomId, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun delete(roomId: RoomId) = roomCache.set(roomId, null)
}
