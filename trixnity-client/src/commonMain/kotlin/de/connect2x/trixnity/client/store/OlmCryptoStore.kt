package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import de.connect2x.trixnity.client.store.cache.MapRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.OlmAccountRepository
import de.connect2x.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import de.connect2x.trixnity.client.store.repository.OlmSessionRepository
import de.connect2x.trixnity.client.store.repository.OutboundMegolmSessionRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OlmCryptoStore(
    olmAccountRepository: OlmAccountRepository,
    olmForgetFallbackKeyAfterRepository: OlmForgetFallbackKeyAfterRepository,
    olmSessionRepository: OlmSessionRepository,
    private val inboundMegolmSessionRepository: InboundMegolmSessionRepository,
    inboundMegolmMessageIndexRepository: InboundMegolmMessageIndexRepository,
    outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    private val tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    private val storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val olmAccountCache =
        MinimalRepositoryObservableCache(olmAccountRepository, tm, storeScope, clock, Duration.INFINITE)
            .also(statisticCollector::addCache)
    private val olmForgetFallbackKeyAfterCache =
        MinimalRepositoryObservableCache(olmForgetFallbackKeyAfterRepository, tm, storeScope, clock, Duration.INFINITE)
            .also(statisticCollector::addCache)

    private val _notBackedUpInboundMegolmSessions = MutableStateFlow<Set<StoredInboundMegolmSession>>(setOf())

    val notBackedUpInboundMegolmSessions = _notBackedUpInboundMegolmSessions.asStateFlow()

    override suspend fun init(coroutineScope: CoroutineScope) {
        storeScope.launch(start = UNDISPATCHED) {
            _notBackedUpInboundMegolmSessions.value = tm.readTransaction {
                inboundMegolmSessionRepository.getByNotBackedUp()
            }
        }
    }

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() {}

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        _notBackedUpInboundMegolmSessions.value = setOf()
        olmAccountCache.deleteAll()
        olmForgetFallbackKeyAfterCache.deleteAll()
        olmSessionsCache.deleteAll()
        inboundMegolmSessionCache.deleteAll()
        inboundMegolmSessionIndexCache.deleteAll()
        outboundMegolmSessionCache.deleteAll()
    }

    private val olmSessionsCache =
        MinimalRepositoryObservableCache(
                olmSessionRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.olmSession,
            )
            .also(statisticCollector::addCache)

    suspend fun getOlmAccount() = olmAccountCache.get(1).first()

    context(transaction: StoreWriteTransaction)
    suspend fun updateOlmAccount(updater: (String?) -> String) = olmAccountCache.update(1) { updater(it) }

    suspend fun getForgetFallbackKeyAfter() = olmForgetFallbackKeyAfterCache.get(1).first()

    context(transaction: StoreWriteTransaction)
    suspend fun updateForgetFallbackKeyAfter(updater: (Instant?) -> Instant?) =
        olmForgetFallbackKeyAfterCache.update(1, updater = updater)

    suspend fun getOlmSessions(senderKey: Curve25519KeyValue) = olmSessionsCache.get(senderKey).first()

    context(transaction: StoreWriteTransaction)
    suspend fun updateOlmSessions(
        senderKey: Curve25519KeyValue,
        updater: (oldSessions: Set<StoredOlmSession>?) -> Set<StoredOlmSession>?,
    ) = olmSessionsCache.update(senderKey, updater = updater)

    private val inboundMegolmSessionCache =
        MapRepositoryObservableCache(
                inboundMegolmSessionRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.inboundMegolmSession,
            )
            .also(statisticCollector::addCache)

    fun getInboundMegolmSession(sessionId: String, roomId: RoomId): Flow<StoredInboundMegolmSession?> =
        inboundMegolmSessionCache.get(MapRepositoryCoroutinesCacheKey(roomId, sessionId))

    fun getInboundMegolmSessions(roomId: RoomId): Flow<Map<String, Flow<StoredInboundMegolmSession?>>> =
        inboundMegolmSessionCache.getByFirstKey(roomId)

    context(transaction: StoreWriteTransaction)
    suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: (oldInboundMegolmSession: StoredInboundMegolmSession?) -> StoredInboundMegolmSession?,
    ) =
        inboundMegolmSessionCache.update(
            MapRepositoryCoroutinesCacheKey(roomId, sessionId),
            updater = updater,
            onPersist = { newValue ->
                _notBackedUpInboundMegolmSessions.update {
                    if (newValue == null || newValue.hasBeenBackedUp)
                        it.filterNot { it.roomId == roomId && it.sessionId == sessionId }.toSet()
                    else it + newValue
                }
            },
        )

    private val inboundMegolmSessionIndexCache =
        MinimalRepositoryObservableCache(
                inboundMegolmMessageIndexRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.inboundMegolmMessageIndex,
            )
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: (oldMegolmSessionIndex: StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?,
    ) =
        inboundMegolmSessionIndexCache.update(
            InboundMegolmMessageIndexRepositoryKey(sessionId, roomId, messageIndex),
            updater = updater,
        )

    private val outboundMegolmSessionCache =
        MinimalRepositoryObservableCache(
                outboundMegolmSessionRepository,
                tm,
                storeScope,
                clock,
                config.cacheExpireDurations.outboundMegolmSession,
            )
            .also(statisticCollector::addCache)

    suspend fun getOutboundMegolmSession(roomId: RoomId): StoredOutboundMegolmSession? =
        outboundMegolmSessionCache.get(roomId).first()

    context(transaction: StoreWriteTransaction)
    suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: (oldOutboundMegolmSession: StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?,
    ) = outboundMegolmSessionCache.update(roomId, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun deleteOutboundMegolmSession(roomId: RoomId) = outboundMegolmSessionCache.set(roomId, null)
}
