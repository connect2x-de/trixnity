package de.connect2x.trixnity.client.store

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.store.cache.FullRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.CrossSigningKeysRepository
import de.connect2x.trixnity.client.store.repository.DeviceKeysRepository
import de.connect2x.trixnity.client.store.repository.KeyChainLinkRepository
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateKey
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateRepository
import de.connect2x.trixnity.client.store.repository.OutdatedKeysRepository
import de.connect2x.trixnity.client.store.repository.RoomKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.SecretKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.SecretsRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.valueOrNull
import de.connect2x.trixnity.crypto.SecretType
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val log = Logger("de.connect2x.trixnity.client.store.KeyStore")

class KeyStore(
    outdatedKeysRepository: OutdatedKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    crossSigningKeysRepository: CrossSigningKeysRepository,
    keyVerificationStateRepository: KeyVerificationStateRepository,
    private val keyChainLinkRepository: KeyChainLinkRepository,
    secretsRepository: SecretsRepository,
    secretKeyRequestRepository: SecretKeyRequestRepository,
    roomKeyRequestRepository: RoomKeyRequestRepository,
    private val tm: StoreTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val outdatedKeysCache =
        MinimalRepositoryObservableCache(
                repository = outdatedKeysRepository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = Duration.INFINITE,
            )
            .also(statisticCollector::addCache)
    private val secretsCache =
        MinimalRepositoryObservableCache(
                repository = secretsRepository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = Duration.INFINITE,
            )
            .also(statisticCollector::addCache)
    private val deviceKeysCache =
        MinimalRepositoryObservableCache(
                repository = deviceKeysRepository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = config.cacheExpireDurations.deviceKeys,
            )
            .also(statisticCollector::addCache)
    private val crossSigningKeysCache =
        MinimalRepositoryObservableCache(
                repository = crossSigningKeysRepository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = config.cacheExpireDurations.crossSigningKeys,
            )
            .also(statisticCollector::addCache)
    private val keyVerificationStateCache =
        MinimalRepositoryObservableCache(
                repository = keyVerificationStateRepository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = config.cacheExpireDurations.keyVerificationState,
            )
            .also(statisticCollector::addCache)
    private val secretKeyRequestCache =
        FullRepositoryObservableCache(
                repository = secretKeyRequestRepository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = config.cacheExpireDurations.secretKeyRequest,
            ) {
                it.content.requestId
            }
            .also(statisticCollector::addCache)
    private val roomKeyRequestCache =
        FullRepositoryObservableCache(
                repository = roomKeyRequestRepository,
                tm = tm,
                cacheScope = storeScope,
                clock = clock,
                expireDuration = config.cacheExpireDurations.roomKeyRequest,
            ) {
                it.content.requestId
            }
            .also(statisticCollector::addCache)

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() {
        keyChainLinkRepository.deleteAll()
        outdatedKeysCache.deleteAll()
        deviceKeysCache.deleteAll()
        crossSigningKeysCache.deleteAll()
        secretKeyRequestCache.deleteAll()
        roomKeyRequestCache.deleteAll()
    }

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        clearCache()
        secretsCache.deleteAll()
        keyVerificationStateCache.deleteAll()
    }

    suspend fun getOutdatedKeys(): Set<UserId> = outdatedKeysCache.get(1).first().orEmpty()

    fun getOutdatedKeysFlow(): Flow<Set<UserId>> = outdatedKeysCache.get(1).map { it.orEmpty() }

    context(transaction: StoreWriteTransaction)
    suspend fun updateOutdatedKeys(updater: (Set<UserId>) -> Set<UserId>) =
        outdatedKeysCache.update(1) { updater(it.orEmpty()) }

    suspend fun getSecrets(): Map<SecretType, StoredSecret> = secretsCache.get(1).first().orEmpty()

    fun getSecretsFlow(): Flow<Map<SecretType, StoredSecret>> = secretsCache.get(1).map { it.orEmpty() }

    context(transaction: StoreWriteTransaction)
    suspend fun updateSecrets(updater: (Map<SecretType, StoredSecret>) -> Map<SecretType, StoredSecret>) =
        secretsCache.update(1) { updater(it ?: mapOf()) }

    /**
     * This prevents deadlocks when no parallel write transactions are allowed, but a second transaction is needed to
     * update outdated keys.
     */
    object SkipOutdatedKeys : CoroutineContext.Element, CoroutineContext.Key<SkipOutdatedKeys> {
        override val key: CoroutineContext.Key<*> = this
    }

    private suspend fun waitForUpdateOutdatedKey(userId: UserId, reason: String, keysAreNull: suspend () -> Boolean) {
        if (currentCoroutineContext()[SkipOutdatedKeys] == null) {
            if (keysAreNull()) {
                log.trace { "add $userId to outdated keys, because key ($reason) not found" }
                tm.writeTransaction { updateOutdatedKeys { it + userId } }
            }
            log.debug { "possibly wait for outdated keys ($reason) of $userId" }
            getOutdatedKeysFlow().first { !it.contains(userId) }
            log.trace { "finished wait for outdated keys ($reason) of $userId" }
        }
    }

    fun getDeviceKeys(userId: UserId): Flow<Map<String, StoredDeviceKeys>?> = flow {
        waitForUpdateOutdatedKey(userId, "device keys") { deviceKeysCache.get(userId).first() == null }
        emitAll(deviceKeysCache.get(userId))
    }

    context(transaction: StoreWriteTransaction)
    suspend fun updateDeviceKeys(
        userId: UserId,
        updater: (Map<String, StoredDeviceKeys>?) -> Map<String, StoredDeviceKeys>?,
    ) = deviceKeysCache.update(userId, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun saveDeviceKeys(userId: UserId, deviceKeys: Map<String, StoredDeviceKeys>) =
        deviceKeysCache.set(userId, deviceKeys)

    context(transaction: StoreWriteTransaction)
    suspend fun deleteDeviceKeys(userId: UserId) = deviceKeysCache.set(userId, null)

    fun getCrossSigningKeys(userId: UserId): Flow<Set<StoredCrossSigningKeys>?> = flow {
        waitForUpdateOutdatedKey(userId, "cross singing keys") { crossSigningKeysCache.get(userId).first() == null }
        emitAll(crossSigningKeysCache.get(userId))
    }

    context(transaction: StoreWriteTransaction)
    suspend fun updateCrossSigningKeys(
        userId: UserId,
        updater: (Set<StoredCrossSigningKeys>?) -> Set<StoredCrossSigningKeys>?,
    ) = crossSigningKeysCache.update(userId, updater = updater)

    context(transaction: StoreWriteTransaction)
    suspend fun deleteCrossSigningKeys(userId: UserId) = crossSigningKeysCache.set(userId, null)

    suspend fun getKeyVerificationState(key: Key): KeyVerificationState? {
        val keyId = key.id
        return keyId?.let {
            keyVerificationStateCache
                .get(KeyVerificationStateKey(keyId = it, keyAlgorithm = key.algorithm))
                .first()
                ?.let { state ->
                    if (state.keyValue == key.value.valueOrNull) state else KeyVerificationState.Blocked(state.keyValue)
                }
        }
    }

    context(transaction: StoreWriteTransaction)
    suspend fun saveKeyVerificationState(key: Key, state: KeyVerificationState) {
        val keyId = key.id
        requireNotNull(keyId)
        keyVerificationStateCache.set(KeyVerificationStateKey(keyId = keyId, keyAlgorithm = key.algorithm), state)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun saveKeyChainLink(keyChainLink: KeyChainLink) = keyChainLinkRepository.save(keyChainLink)

    suspend fun getKeyChainLinksBySigningKey(userId: UserId, signingKey: Key.Ed25519Key) = tm.readTransaction {
        keyChainLinkRepository.getBySigningKey(userId, signingKey)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteKeyChainLinksBySignedKey(userId: UserId, signedKey: Key.Ed25519Key) =
        keyChainLinkRepository.deleteBySignedKey(userId, signedKey)

    fun getAllSecretKeyRequestsFlow() = secretKeyRequestCache.readAll().flattenValues()

    suspend fun getAllSecretKeyRequests() = getAllSecretKeyRequestsFlow().first()

    context(transaction: StoreWriteTransaction)
    suspend fun addSecretKeyRequest(request: StoredSecretKeyRequest) {
        secretKeyRequestCache.set(request.content.requestId, request)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteSecretKeyRequest(requestId: String) {
        secretKeyRequestCache.set(requestId, null)
    }

    fun getAllRoomKeyRequestsFlow() = roomKeyRequestCache.readAll().flattenValues()

    suspend fun getAllRoomKeyRequests() = getAllRoomKeyRequestsFlow().first()

    context(transaction: StoreWriteTransaction)
    suspend fun addRoomKeyRequest(request: StoredRoomKeyRequest) {
        roomKeyRequestCache.set(request.content.requestId, request)
    }

    context(transaction: StoreWriteTransaction)
    suspend fun deleteRoomKeyRequest(requestId: String) {
        roomKeyRequestCache.set(requestId, null)
    }
}
