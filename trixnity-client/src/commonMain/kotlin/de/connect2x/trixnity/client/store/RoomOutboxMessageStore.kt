package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.FullDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

class RoomOutboxMessageStore(
    roomOutboxMessageRepository: RoomOutboxMessageRepository,
    tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomOutboxMessageCache = FullDeleteByRoomIdRepositoryObservableCache(
        roomOutboxMessageRepository,
        tm,
        storeScope,
        clock,
        config.cacheExpireDurations.roomOutboxMessage,
        { RoomOutboxMessageRepositoryKey(it.roomId, it.transactionId) }) {
        it.roomId
    }.also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        roomOutboxMessageCache.deleteAll()
    }

    fun getAll(): Flow<Map<RoomOutboxMessageRepositoryKey, Flow<RoomOutboxMessage<*>?>>> =
        roomOutboxMessageCache.readAll()

    context(transaction: StoreWriteTransaction)
    suspend fun update(
        roomId: RoomId,
        transactionId: String,
        updater: (RoomOutboxMessage<*>?) -> RoomOutboxMessage<*>?
    ) =
        roomOutboxMessageCache.update(RoomOutboxMessageRepositoryKey(roomId, transactionId), updater = updater)

    fun get(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageCache.get(RoomOutboxMessageRepositoryKey(roomId, transactionId))

    fun getAsFlow(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageCache.get(RoomOutboxMessageRepositoryKey(roomId, transactionId))

    context(transaction: StoreWriteTransaction)
    suspend fun deleteByRoomId(roomId: RoomId) = roomOutboxMessageCache.deleteByRoomId(roomId)
}
