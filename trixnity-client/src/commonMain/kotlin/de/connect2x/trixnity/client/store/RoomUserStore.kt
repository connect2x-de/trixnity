package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RoomUserReceiptsRepository
import de.connect2x.trixnity.client.store.repository.RoomUserRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class RoomUserStore(
    roomUserRepository: RoomUserRepository,
    roomUserReceiptsRepository: RoomUserReceiptsRepository,
    tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomUserCache =
        MapDeleteByRoomIdRepositoryObservableCache(
                roomUserRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.roomUser,
            ) {
                it.firstKey
            }
            .also(statisticCollector::addCache)

    private val roomUserReceiptsCache =
        MapDeleteByRoomIdRepositoryObservableCache(
                roomUserReceiptsRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.roomUserReceipts,
            ) {
                it.firstKey
            }
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        roomUserCache.deleteAll()
        roomUserReceiptsCache.deleteAll()
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteByRoomId(roomId: RoomId) {
        roomUserCache.deleteByRoomId(roomId)
        roomUserReceiptsCache.deleteByRoomId(roomId)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteReceiptsByRoomId(roomId: RoomId) {
        roomUserReceiptsCache.deleteByRoomId(roomId)
    }

    fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>> = roomUserCache.readByFirstKey(roomId)

    fun get(userId: UserId, roomId: RoomId): Flow<RoomUser?> =
        roomUserCache.get(MapRepositoryCoroutinesCacheKey(roomId, userId))

    fun getAllReceipts(roomId: RoomId): Flow<Map<UserId, Flow<RoomUserReceipts?>>> =
        roomUserReceiptsCache.readByFirstKey(roomId)

    fun getReceipts(userId: UserId, roomId: RoomId): Flow<RoomUserReceipts?> =
        roomUserReceiptsCache.get(MapRepositoryCoroutinesCacheKey(roomId, userId))

    context(transaction: StoreWriteTransaction)
    suspend fun update(userId: UserId, roomId: RoomId, updater: (oldRoomUser: RoomUser?) -> RoomUser?) =
        roomUserCache.update(MapRepositoryCoroutinesCacheKey(roomId, userId), updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun updateReceipts(
        userId: UserId,
        roomId: RoomId,
        updater: (oldRoomUser: RoomUserReceipts?) -> RoomUserReceipts?,
    ) = roomUserReceiptsCache.update(MapRepositoryCoroutinesCacheKey(roomId, userId), updater = updater)
}
