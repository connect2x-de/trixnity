package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RoomStateRepository
import de.connect2x.trixnity.client.store.repository.RoomStateRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.RedactedStateEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.contentType
import io.ktor.util.reflect.*
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

class RoomStateStore(
    private val roomStateRepository: RoomStateRepository,
    private val tm: StoreTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomStateCache =
        MapDeleteByRoomIdRepositoryObservableCache(
                roomStateRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.roomState,
            ) {
                it.firstKey.roomId
            }
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() = deleteAll()

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        roomStateCache.deleteAll()
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteByRoomId(roomId: RoomId) {
        roomStateCache.deleteByRoomId(roomId)
    }

    private fun <C : StateEventContent> findType(eventContentClass: KClass<C>): String {
        return contentMappings.state.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException(
                "Cannot find state event, because it is not supported. You need to register it first."
            )
    }

    context(transaction: StoreWriteTransaction)
    suspend fun save(event: StateBaseEvent<*>, skipWhenAlreadyPresent: Boolean = false) {
        save(listOf(event), skipWhenAlreadyPresent)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun save(events: List<StateBaseEvent<*>>, skipWhenAlreadyPresent: Boolean = false) {

        events
            .mapNotNull { event ->
                val roomId = event.roomId ?: return@mapNotNull null
                val eventType =
                    when (val content = event.content) {
                        is UnknownEventContent -> content.eventType
                        is RedactedStateEventContent -> content.eventType
                        else -> contentMappings.state.contentType(event.content)
                    }
                EventWithType(roomId, event, eventType, event.stateKey)
            }
            .asReversed()
            .distinct()
            .asReversed()
            .forEach { (roomId, event, type, stateKey) ->
                if (skipWhenAlreadyPresent)
                    roomStateCache.update(
                        MapRepositoryCoroutinesCacheKey(RoomStateRepositoryKey(roomId, type), stateKey)
                    ) {
                        if (it is ClientEvent.StrippedStateEvent) event else it ?: event
                    }
                else
                    roomStateCache.set(
                        MapRepositoryCoroutinesCacheKey(RoomStateRepositoryKey(roomId, type), stateKey),
                        event,
                    )
            }
    }

    /** Allows to distinct by [roomId], [type] and [stateKey] */
    private data class EventWithType(
        val roomId: RoomId,
        val event: StateBaseEvent<*>,
        val type: String,
        val stateKey: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as EventWithType

            if (roomId != other.roomId) return false
            if (type != other.type) return false
            if (stateKey != other.stateKey) return false

            return true
        }

        override fun hashCode(): Int {
            var result = roomId.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + stateKey.hashCode()
            return result
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <C : StateEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Flow<StateBaseEvent<C>?>>> {
        val eventType = findType(eventContentClass)
        return roomStateCache.getByFirstKey(RoomStateRepositoryKey(roomId, eventType)).mapLatest { value ->
            value.mapValues { entry ->
                entry.value.map {
                    if (it?.content?.instanceOf(eventContentClass) == true) {
                        @Suppress("UNCHECKED_CAST")
                        it as StateBaseEvent<C>
                    } else null
                }
            }
        }
    }

    fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        stateKey: String,
    ): Flow<StateBaseEvent<C>?> {
        val eventType = findType(eventContentClass)
        @Suppress("UNCHECKED_CAST")
        return roomStateCache
            .get(MapRepositoryCoroutinesCacheKey(RoomStateRepositoryKey(roomId, eventType), stateKey))
            .map { if (it?.content?.instanceOf(eventContentClass) == true) it else null } as Flow<StateBaseEvent<C>?>
    }

    suspend fun <C : StateEventContent> getByRooms(
        roomIds: Set<RoomId>,
        eventContentClass: KClass<C>,
        stateKey: String,
    ): List<StateBaseEvent<C>> {
        val eventType = findType(eventContentClass)
        @Suppress("UNCHECKED_CAST")
        return tm.readTransaction { roomStateRepository.getByRooms(roomIds, eventType, stateKey) }
            .mapNotNull { if (it.content.instanceOf(eventContentClass)) it else null }
            .filterIsInstance<StateBaseEvent<C>>()
    }
}
