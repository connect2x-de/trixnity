package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenNotNull
import de.connect2x.trixnity.client.store.cache.FullRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.NotificationRepository
import de.connect2x.trixnity.client.store.repository.NotificationStateRepository
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
import de.connect2x.trixnity.core.model.RoomId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NotificationStore(
    private val notificationRepository: NotificationRepository,
    private val notificationUpdateRepository: NotificationUpdateRepository,
    private val notificationStateRepository: NotificationStateRepository,
    tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val notificationCache =
        FullRepositoryObservableCache(
                notificationRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.notification,
            ) {
                it.id
            }
            .also(statisticCollector::addCache)

    private val notificationUpdateCache =
        FullRepositoryObservableCache(
                notificationUpdateRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.notification,
            ) {
                it.id
            }
            .also(statisticCollector::addCache)

    private val notificationStateCache =
        FullRepositoryObservableCache(
                notificationStateRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.notification,
            ) {
                it.roomId
            }
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        notificationCache.deleteAll()
        notificationUpdateCache.deleteAll()
        notificationStateCache.deleteAll()
    }

    fun getAll(): Flow<Map<String, Flow<StoredNotification?>>> = notificationCache.readAll()

    fun getAllUpdates(): Flow<Map<String, Flow<StoredNotificationUpdate?>>> = notificationUpdateCache.readAll()

    fun getAllState() = notificationStateCache.readAll()

    fun getById(id: String): Flow<StoredNotification?> = notificationCache.get(id)

    context(transaction: StoreWriteTransaction)
    suspend fun save(value: StoredNotification) = notificationCache.set(value.id, value)

    context(transaction: StoreWriteTransaction)
    suspend fun save(id: String, value: StoredNotification?) = notificationCache.set(id, value)

    context(transaction: StoreWriteTransaction)
    suspend fun saveAllUpdates(values: List<StoredNotificationUpdate>) {
        values.forEach { notificationUpdateCache.set(it.id, it) }
    }

    context(transaction: StoreWriteTransaction)
    suspend fun update(id: String, updater: (oldNotification: StoredNotification?) -> StoredNotification?) =
        notificationCache.update(id, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun updateState(
        roomId: RoomId,
        updater: (oldNotificationState: StoredNotificationState?) -> StoredNotificationState?,
    ) = notificationStateCache.update(roomId, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun updateUpdate(
        id: String,
        updater: (oldNotificationUpdate: StoredNotificationUpdate?) -> StoredNotificationUpdate?,
    ) = notificationUpdateCache.update(id, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun delete(id: String) = notificationCache.set(id, null)

    context(transaction: StoreWriteTransaction)
    suspend fun deleteNotificationsByRoomId(roomId: RoomId) {
        notificationRepository.deleteByRoomId(roomId)
        // TODO (fhilgers): This currently loads the values from store if they weren't loaded yet,
        //                  just to evict them again. Ideally the cache would have a way to just synchronously
        //                  remove values from it.
        //                  The throttle has to be zero to not actually yield inside a transaction.
        notificationCache.readAll().flattenNotNull(Duration.ZERO).first().forEach { (key, value) ->
            if (value.roomId == roomId) notificationCache.setCacheOnly(key, null)
        }
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteNotificationUpdatesByRoomId(roomId: RoomId) {
        notificationUpdateRepository.deleteByRoomId(roomId)
        // TODO (fhilgers): This currently loads the values from store if they weren't loaded yet,
        //                  just to evict them again. Ideally the cache would have a way to just synchronously
        //                  remove values from it.
        //                  The throttle has to be zero to not actually yield inside a transaction.
        notificationUpdateCache.readAll().flattenNotNull(Duration.ZERO).first().forEach { (key, value) ->
            if (value.roomId == roomId) notificationUpdateCache.setCacheOnly(key, null)
        }
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteNotificationStateByRoomId(roomId: RoomId) {
        notificationStateCache.set(roomId, null)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteByRoomId(roomId: RoomId) {
        deleteNotificationStateByRoomId(roomId)
        deleteNotificationsByRoomId(roomId)
        deleteNotificationUpdatesByRoomId(roomId)
    }
}
