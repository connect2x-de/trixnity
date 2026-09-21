package de.connect2x.trixnity.client.key

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel.CrossSigned
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.isVerified
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RoomKeyBundleEventContent
import de.connect2x.trixnity.core.model.keys.ExportedSessionKeyValue
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.RoomKeyBundle
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSession
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import de.connect2x.trixnity.utils.nextString
import de.connect2x.trixnity.utils.toByteArrayFlow
import kotlin.time.Duration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val api: MatrixClientServerApiClient,
    private val currentSyncState: CurrentSyncState,
    private val cryptoDriver: CryptoDriver,
    private val json: Json,
    private val userInfo: UserInfo,
) : KeyShareService, EventHandler {
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
}
