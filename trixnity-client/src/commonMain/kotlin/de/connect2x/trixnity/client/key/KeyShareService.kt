package de.connect2x.trixnity.client.key

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel.CrossSigned
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoredRoomKeyBundles
import de.connect2x.trixnity.client.store.isVerified
import de.connect2x.trixnity.client.utils.retryLoop
import de.connect2x.trixnity.clientserverapi.client.DownloadLimitExceededException
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.ClientEventEmitter
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.RoomKeyBundleEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.keys.ExportedSessionKeyValue
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.RoomKeyBundle
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSession
import de.connect2x.trixnity.crypto.driver.useAll
import de.connect2x.trixnity.crypto.invoke
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventContainer
import de.connect2x.trixnity.crypto.olm.InboundMegolmSessionSource
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import de.connect2x.trixnity.crypto.olm.OlmEventHandler
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.utils.nextString
import de.connect2x.trixnity.utils.toByteArrayFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val log = Logger("de.connect2x.trixnity.client.key.KeyShareService")

interface KeyShareService {
    suspend fun shareRoomKeyBundle(roomId: RoomId, userId: UserId): Result<Unit>
}

class KeyShareServiceImpl(
    private val accountStore: AccountStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyStore: KeyStore,
    private val keyBackupService: KeyBackupService,
    private val dehydratedDeviceService: DehydratedDeviceService,
    private val olmEncryptionService: OlmEncryptionService,
    private val mediaService: MediaService,
    private val olmEventHandler: OlmEventHandler,
    private val api: MatrixClientServerApiClient,
    private val currentSyncState: CurrentSyncState,
    private val cryptoDriver: CryptoDriver,
    private val json: Json,
    private val clock: Clock,
    private val userInfo: UserInfo,
    private val tm: StoreTransactionManager,
) : KeyShareService, EventHandler {

    companion object {
        const val MAX_ROOM_KEY_BUNDLE_SIZE: Long = 100 * 1024 * 1024 // 100 MB
    }

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync
            .subscribeEventList(ClientEventEmitter.Priority.DEFAULT, ::handleMembershipChanges)
            .unsubscribeOnCompletion(scope)
        olmEventHandler.subscribe(::handleSharedKeyBundle).unsubscribeOnCompletion(scope)
        scope.launch { continuouslyApplyRoomKeyBundles() }
    }

    override suspend fun shareRoomKeyBundle(roomId: RoomId, userId: UserId): Result<Unit> = runCatching {
        waitForDeviceSetup()

        val recipients =
            keyStore
                .getDeviceKeys(userId)
                .first()
                ?.values
                ?.filter { it.trustLevel is CrossSigned }
                ?.map { userId to it.value.signed.deviceId }
                ?.toSet()
        if (recipients.isNullOrEmpty()) {
            log.debug { "not sending any key bundle for $userId in $roomId, because no cross signed devices found" }
            return@runCatching
        }

        keyBackupService.loadMegolmSessions(roomId)

        val sharedKeyBundle = createRoomKeyBundle(roomId)
        val sharedKeyBundleJson = json.encodeToString(sharedKeyBundle)
        val encryptedFile =
            mediaService.prepareUploadEncryptedMedia(
                content = sharedKeyBundleJson.encodeToByteArray().toByteArrayFlow()
            )
        try {
            val uploadedMediaUri = mediaService.uploadMedia(encryptedFile.url, keepMediaInCache = false).getOrThrow()
            val eventContent = RoomKeyBundleEventContent(roomId, encryptedFile.copy(url = uploadedMediaUri))

            val eventsToSend =
                olmEncryptionService
                    .encryptOlm(eventContent, recipients)
                    .mapNotNull { (recipient, encryptOlmResult) ->
                        encryptOlmResult
                            .onFailure {
                                val e = it as? EncryptOlmError
                                when (e) {
                                    is EncryptOlmError.CryptoDriverError -> throw e
                                    is EncryptOlmError.NetworkError -> throw e

                                    is EncryptOlmError.DehydratedDeviceNotCrossSigned -> {
                                        log.debug {
                                            "will not send key bundle to $recipient, because dehydrated device not cross signed"
                                        }
                                    }

                                    is EncryptOlmError.NoOlmSupported -> {
                                        log.debug {
                                            "will not send key bundle to $recipient, because olm not supported"
                                        }
                                    }

                                    is EncryptOlmError.RemoteHomeserverNotReachable -> {
                                        log.warn {
                                            "will not send key bundle to $recipient, because remote homeserver not reachable and therefore new olm session could not be created"
                                        }
                                    }

                                    null -> {
                                        log.error(it) { "unexpected error" }
                                    }
                                }
                            }
                            .getOrNull()
                            ?.let { recipient to it }
                    }
                    .groupBy { it.first.first }
                    .mapValues { it.value.associate { it.first.second to it.second } }

            if (eventsToSend.isEmpty()) {
                log.debug {
                    "not sending any key bundle for $userId in $roomId, because no event could be successfully encrypted"
                }
                return@runCatching
            }
            log.debug { "send key bundle for $roomId to devices: ${eventsToSend.mapValues { it.value.keys }}" }
            api.user.sendToDevice(eventsToSend, SecureRandom.nextString(22)).getOrThrow()
        } catch (error: Throwable) {
            withContext(NonCancellable) { mediaService.removeCachedMedia(encryptedFile.url) }
            throw error
        }
    }

    private suspend fun waitForDeviceSetup() {
        log.debug { "wait for sync to be running" }
        currentSyncState.first { it == SyncState.RUNNING }
        log.debug { "wait for this device to be cross signed" }
        keyStore
            .getDeviceKeys(userInfo.userId)
            .filterNotNull()
            .map { deviceKeys -> deviceKeys[userInfo.deviceId]?.trustLevel }
            .first { ownTrustLevel -> ownTrustLevel is CrossSigned && ownTrustLevel.isVerified }
        log.debug { "wait for dehydration to be finished" }
        dehydratedDeviceService.pendingRehydration.first { it.not() }
    }

    internal suspend fun createRoomKeyBundle(roomId: RoomId): RoomKeyBundle {
        log.debug { "creating key bundle for $roomId" }
        val inboundMegolmSessions =
            olmCryptoStore.getInboundMegolmSessions(roomId).flatten(Duration.INFINITE).first().values
        val (sharedInboundMegolmSessions, notSharedInboundMegolmSessions) =
            inboundMegolmSessions.asSequence().filterNotNull().partition { it.sharedHistory }
        val account = checkNotNull(accountStore.getAccount())
        return RoomKeyBundle(
            roomKeys =
                sharedInboundMegolmSessions.map { session ->
                    val sessionKey =
                        cryptoDriver.megolm.inboundGroupSession
                            .fromPickle(session.pickled, cryptoDriver.key.pickleKey(account.olmPickleKey))
                            .use(InboundGroupSession::exportAtFirstKnownIndex)
                    RoomKeyBundle.HistoricRoomKey(
                        roomId = session.roomId,
                        senderKey = session.senderKey,
                        senderClaimedKeys = Keys(Key.Ed25519Key(null, session.senderSigningKey)),
                        sessionId = session.sessionId,
                        sessionKey = ExportedSessionKeyValue.of(sessionKey),
                    )
                },
            withheld =
                notSharedInboundMegolmSessions.map { session ->
                    RoomKeyBundle.RoomKeyWhithheld(
                        roomId = session.roomId,
                        senderKey = session.senderKey,
                        sessionId = session.sessionId,
                        code = RoomKeyWithheldCode.HistoryNotShared,
                        reason = "room key not marked as shared",
                    )
                },
        )
    }

    internal suspend fun handleMembershipChanges(memberEvents: List<ClientEvent.StateBaseEvent<MemberEventContent>>) {
        val deduplicatedMemberEvents =
            memberEvents
                .asReversed()
                .filter { it.stateKey == userInfo.userId.full }
                .distinctBy { it.roomId }
                .asReversed()
        val updates: List<Pair<RoomId, StoredRoomKeyBundlesUpdate>> =
            deduplicatedMemberEvents.mapNotNull { newMembershipEvent ->
                val roomId = newMembershipEvent.roomId ?: return@mapNotNull null
                when (val newMembership = newMembershipEvent.content.membership) {
                    Membership.INVITE -> {
                        val acceptFrom = newMembershipEvent.sender
                        log.debug { "update key bundle state for $roomId acceptFrom=$acceptFrom" }
                        roomId to
                            StoredRoomKeyBundlesUpdate {
                                it?.copy(acceptFrom = acceptFrom)
                                    ?: StoredRoomKeyBundles(
                                        roomId = roomId,
                                        acceptFrom = acceptFrom,
                                        acceptUntil = Instant.DISTANT_FUTURE, // wait until JOIN
                                        bundles = setOf(),
                                    )
                            }
                    }
                    Membership.JOIN -> {
                        val previousMembership =
                            (newMembershipEvent.unsigned?.previousContent as? MemberEventContent)?.membership
                        when (previousMembership) {
                            Membership.INVITE -> {
                                val acceptUntil =
                                    newMembershipEvent.originTimestamp
                                        ?.let(Instant::fromEpochMilliseconds)
                                        ?.plus(1.days) ?: return
                                log.debug { "update key bundle state for $roomId acceptUntil=$acceptUntil" }
                                roomId to StoredRoomKeyBundlesUpdate { it?.copy(acceptUntil = acceptUntil) }
                            }
                            Membership.JOIN -> {
                                log.trace {
                                    "ignore key bundle state update for $roomId because membership did not change"
                                }
                                null
                            }
                            Membership.KNOCK,
                            Membership.LEAVE,
                            Membership.BAN,
                            null -> {
                                log.trace {
                                    "remove key bundle state for $roomId because previous known membership was $previousMembership"
                                }
                                roomId to StoredRoomKeyBundlesUpdate { null }
                            }
                        }
                    }
                    Membership.KNOCK -> {
                        log.trace {
                            "ignore key bundle state update for $roomId because new membership is $newMembership"
                        }
                        null
                    }
                    Membership.LEAVE,
                    Membership.BAN -> {
                        log.trace { "remove key bundle state for $roomId because new membership is $newMembership" }
                        roomId to StoredRoomKeyBundlesUpdate { null }
                    }
                }
            }
        if (updates.isNotEmpty()) {
            tm.writeTransaction {
                updates.forEach { (roomId, storedRoomKeyBundlesUpdate) ->
                    keyStore.updateRoomKeyBundles(roomId, storedRoomKeyBundlesUpdate::invoke)
                }
            }
        }
    }

    private suspend fun handleSharedKeyBundle(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is RoomKeyBundleEventContent) {
            val userId = event.decrypted.sender
            val deviceKeys = keyStore.getDeviceKeys(userId).first()
            val deviceId =
                deviceKeys
                    ?.values
                    ?.find { it.value.signed.keys.keys.any { it.value == event.encrypted.content.senderKey } }
                    ?.value
                    ?.signed
                    ?.deviceId ?: return
            handleSharedKeyBundle(userId, deviceId, content)
        }
    }

    internal suspend fun handleSharedKeyBundle(
        senderUserId: UserId,
        senderDeviceId: String,
        eventContent: RoomKeyBundleEventContent,
    ) {
        if (senderUserId == userInfo.userId) {
            log.warn { "ignore key bundle from own userId" }
            return
        }
        val sender = InboundMegolmSessionSource.KeyBundle.Sender(senderUserId, senderDeviceId)
        val roomId = eventContent.roomId
        log.debug { "update key bundle state for $roomId with bundle from $sender" }
        tm.writeTransaction {
            keyStore.updateRoomKeyBundles(roomId) {
                val bundle = StoredRoomKeyBundles.Bundle(senderUserId, senderDeviceId, eventContent)
                it?.copy(bundles = it.bundles + bundle)
                    ?: StoredRoomKeyBundles(
                        roomId = roomId,
                        acceptFrom = null,
                        acceptUntil = clock.now() + 1.days, // we expect an invitation to come
                        bundles = setOf(bundle),
                    )
            }
        }
    }

    internal suspend fun continuouslyApplyRoomKeyBundles() {
        currentSyncState.retryLoop {
            keyStore.getRoomKeyBundles().flattenValues().collect { allRoomKeyBundles ->
                allRoomKeyBundles.forEach { roomKeyBundles ->
                    applyRoomKeyBundle(roomKeyBundles).onFailure {
                        log.warn(it) { "failed to apply key bundle for ${roomKeyBundles.roomId}" }
                    }
                }
            }
        }
    }

    internal suspend fun applyRoomKeyBundle(roomKeyBundles: StoredRoomKeyBundles): Result<Unit> = runCatching {
        val roomId = roomKeyBundles.roomId
        val acceptFrom = roomKeyBundles.acceptFrom
        val acceptUntil = roomKeyBundles.acceptUntil
        if (acceptUntil < clock.now()) {
            log.debug { "remove key bundle state for $roomId because it expired" }
            tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { null } }
            return@runCatching
        }
        val roomKeyBundles = roomKeyBundles.bundles.filter { it.senderUserId == acceptFrom }
        if (roomKeyBundles.isEmpty()) {
            log.debug { "no room key bundle found for $roomId from $acceptFrom" }
            return@runCatching
        }
        log.debug { "try to apply room key bundle for $roomId from $acceptFrom" }
        roomKeyBundles.forEach { roomKeyBundle ->
            val sharedKeyBundleJson =
                mediaService
                    .getEncryptedMedia(roomKeyBundle.content.file, MAX_ROOM_KEY_BUNDLE_SIZE, saveToCache = false)
                    .fold(
                        onSuccess = { it },
                        onFailure = {
                            when (it) {
                                is DownloadLimitExceededException -> {
                                    log.warn { "key bundle download limit exceeded for $roomId" }
                                    null
                                }

                                is MatrixServerException if it.errorResponse is ErrorResponse.NotFound -> {
                                    log.warn { "key bundle not found on server for $roomId" }
                                    null
                                }

                                else -> throw it
                            }
                        },
                    )
                    ?.toByteArray()
                    ?.decodeToString()
            if (sharedKeyBundleJson == null) {
                log.warn { "could not find or decode key bundle" }
                tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { null } }
                return@runCatching
            }
            val sharedKeyBundle =
                try {
                    json.decodeFromString<RoomKeyBundle>(sharedKeyBundleJson)
                } catch (e: Exception) {
                    log.warn(e) { "failed deserializing key bundle" }
                    tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { null } }
                    return@runCatching
                }
            val roomKeys = sharedKeyBundle.roomKeys
            if (!roomKeys.isNullOrEmpty()) {
                val account = checkNotNull(accountStore.getAccount())
                val storedInboundMegolmSessions = roomKeys.mapNotNull { roomKey ->
                    try {
                        val (firstKnownIndex, pickledSession) =
                            useAll(
                                { cryptoDriver.megolm.exportedSessionKey(roomKey.sessionKey) },
                                { cryptoDriver.megolm.inboundGroupSession.import(it) },
                            ) { _, inboundGroupSession ->
                                inboundGroupSession.firstKnownIndex to
                                    inboundGroupSession.pickle(cryptoDriver.key.pickleKey(account.olmPickleKey))
                            }
                        val senderSigningKey =
                            roomKey.senderClaimedKeys.filterIsInstance<Key.Ed25519Key>().firstOrNull()
                        if (senderSigningKey == null) {
                            log.warn { "senderClaimedKey must not be empty" }
                            return@mapNotNull null
                        }
                        StoredInboundMegolmSession(
                            senderKey = roomKey.senderKey,
                            sessionId = roomKey.sessionId,
                            roomId = roomId,
                            firstKnownIndex = firstKnownIndex.toLong(),
                            source =
                                InboundMegolmSessionSource.KeyBundle(
                                    setOf(
                                        InboundMegolmSessionSource.KeyBundle.Sender(
                                            roomKeyBundle.senderUserId,
                                            roomKeyBundle.senderDeviceId,
                                        )
                                    )
                                ),
                            hasBeenBackedUp = false,
                            senderSigningKey = senderSigningKey.value,
                            sharedHistory = true, // is shared with us
                            pickled = pickledSession,
                        )
                    } catch (exception: CryptoDriverException) {
                        log.warn(exception) { "ignore room key from key bundle of $roomId" }
                        null
                    }
                }
                tm.writeTransaction {
                    storedInboundMegolmSessions.forEach { storedInboundMegolmSession ->
                        olmCryptoStore.updateInboundMegolmSession(storedInboundMegolmSession.sessionId, roomId) {
                            // TODO theoretically we could lift the source to Creator or add the sender to the
                            //  KeyBundle (e.g. by compare export at an index)
                            if (it != null && it.firstKnownIndex <= storedInboundMegolmSession.firstKnownIndex) {
                                it
                            } else {
                                storedInboundMegolmSession
                            }
                        }
                    }
                }
            }
        }
        log.debug { "successfully applied room key bundle for $roomId from $acceptFrom" }
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { null } }
    }

    private fun interface StoredRoomKeyBundlesUpdate {
        fun invoke(value: StoredRoomKeyBundles?): StoredRoomKeyBundles?
    }
}
