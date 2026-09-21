package de.connect2x.trixnity.client.key

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.getInMemoryAccountStore
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.DehydratedDeviceServiceMock
import de.connect2x.trixnity.client.mocks.KeyBackupServiceMock
import de.connect2x.trixnity.client.mocks.MediaServiceMock
import de.connect2x.trixnity.client.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel.CrossSigned
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.user.SendToDevice
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.RoomKeyBundleEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.ExportedSessionKeyValue
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.RoomKeyBundle
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class KeyShareServiceTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val cryptoDriver = VodozemacCryptoDriver

    private val ownUserId = UserId("alice", "server")
    private val ownDeviceId = "DEV"
    private val recipientUserId = UserId("bob", "server")
    private val recipientDeviceId = "BOB"
    private val senderKey = Curve25519KeyValue("senderKey")

    private val roomId = RoomId("!room:server")

    private val accountStore = getInMemoryAccountStore()
    private val olmCryptoStore = getInMemoryOlmStore()
    private val keyStore = getInMemoryKeyStore()

    private val currentSyncState = MutableStateFlow(SyncState.RUNNING)
    private val json = de.connect2x.trixnity.core.serialization.createMatrixEventJson()
    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig, json)

    private val keyBackupService = KeyBackupServiceMock()
    private val dehydratedDeviceService =
        DehydratedDeviceServiceMock().apply { scheduleSetup { pendingRehydration.value = false } }
    private val olmEncryptionService = OlmEncryptionServiceMock()
    private val mediaService = MediaServiceMock()

    private val cut =
        KeyShareServiceImpl(
                accountStore = accountStore,
                olmCryptoStore = olmCryptoStore,
                keyStore = keyStore,
                keyBackupService = keyBackupService,
                dehydratedDeviceService = dehydratedDeviceService,
                olmEncryptionService = olmEncryptionService,
                mediaService = mediaService,
                api = api,
                currentSyncState = CurrentSyncState(currentSyncState),
                cryptoDriver = cryptoDriver,
                json = json,
                userInfo = UserInfo(ownUserId, ownDeviceId, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            )
            .apply { startInCoroutineScope(testScope.backgroundScope) }

    @Test
    fun `createRoomKeyBundle - partition keys`() = runTest {
        val outboundSession1 = cryptoDriver.megolm.groupSession()
        val outboundSession2 = cryptoDriver.megolm.groupSession()
        outboundSession1.encrypt("bla")
        outboundSession2.encrypt("blub")
        val inboundSession1 = cryptoDriver.megolm.inboundGroupSession(sessionKey = outboundSession1.sessionKey)
        val inboundSession2 = cryptoDriver.megolm.inboundGroupSession(sessionKey = outboundSession2.sessionKey)
        val sharedSession =
            StoredInboundMegolmSession(
                senderKey = senderKey,
                sessionId = inboundSession1.sessionId,
                roomId = roomId,
                firstKnownIndex = 24,
                hasBeenBackedUp = true,
                isTrusted = true,
                senderSigningKey = Ed25519KeyValue("edKey"),
                forwardingCurve25519KeyChain = listOf(),
                pickled = inboundSession1.pickle(),
                sharedHistory = true,
            )
        val nonSharedSession =
            StoredInboundMegolmSession(
                senderKey = senderKey,
                sessionId = inboundSession2.sessionId,
                roomId = roomId,
                firstKnownIndex = 24,
                hasBeenBackedUp = true,
                isTrusted = true,
                senderSigningKey = Ed25519KeyValue("edKey"),
                forwardingCurve25519KeyChain = listOf(),
                pickled = inboundSession1.pickle(),
                sharedHistory = false,
            )
        tm.writeTransaction {
            olmCryptoStore.updateInboundMegolmSession(inboundSession1.sessionId, roomId) { sharedSession }
            olmCryptoStore.updateInboundMegolmSession(inboundSession2.sessionId, roomId) { nonSharedSession }
        }
        cut.createRoomKeyBundle(roomId) shouldBe
            RoomKeyBundle(
                roomKeys =
                    listOf(
                        RoomKeyBundle.HistoricRoomKey(
                            roomId = roomId,
                            senderKey = senderKey,
                            sessionId = inboundSession1.sessionId,
                            senderClaimedKeys = Keys(Ed25519Key(null, Ed25519KeyValue("edKey"))),
                            sessionKey = ExportedSessionKeyValue.of(inboundSession1.exportAtFirstKnownIndex()),
                        )
                    ),
                withheld =
                    listOf(
                        RoomKeyBundle.RoomKeyWhithheld(
                            roomId = roomId,
                            senderKey = senderKey,
                            sessionId = inboundSession2.sessionId,
                            code = RoomKeyWithheldCode.HistoryNotShared,
                            reason = "room key not marked as shared",
                        )
                    ),
            )
    }

    @Test
    fun `shareRoomKeyBundle - successfully shares bundle with cross signed devices`() = runTest {
        prepareOwnDevice()
        prepareRecipientDevice()
        prepareEncryptedMedia()
        prepareOlmEncryption()
        var sentEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null

        apiConfig.endpoints { matrixJsonEndpoint(SendToDevice("m.room.encrypted", "*")) { sentEvents = it.messages } }

        cut.shareRoomKeyBundle(roomId, recipientUserId).getOrThrow()

        sentEvents shouldBe
            mapOf(
                recipientUserId to
                    mapOf(
                        recipientDeviceId to
                            OlmEncryptedToDeviceEventContent(ciphertext = mapOf(), senderKey = senderKey)
                    )
            )

        mediaService.uploadMediaCalled.value shouldBe "mxc://example.org/encrypted-key-bundle"
        olmEncryptionService.encryptOlmCalled?.second shouldBe recipientUserId
        olmEncryptionService.encryptOlmCalled?.third shouldBe recipientDeviceId

        val eventContent = olmEncryptionService.encryptOlmCalled?.first as RoomKeyBundleEventContent
        eventContent.roomId shouldBe roomId
        eventContent.file.url shouldBe "mxc://example.org/uploaded-key-bundle"
    }

    @Test
    fun `shareRoomKeyBundle - does not send when recipient has no cross signed devices`() = runTest {
        prepareOwnDevice()
        prepareEncryptedMedia()

        tm.writeTransaction {
            keyStore.updateDeviceKeys(recipientUserId) {
                mapOf(
                    recipientDeviceId to
                        StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(recipientUserId, recipientDeviceId, setOf(), keysOf()),
                                mapOf(),
                            ),
                            KeySignatureTrustLevel.Valid(false),
                        )
                )
            }
        }

        cut.shareRoomKeyBundle(roomId, recipientUserId).getOrThrow()

        mediaService.uploadMediaCalled.value shouldBe null
        olmEncryptionService.encryptOlmCalled shouldBe null
    }

    @Test
    fun `shareRoomKeyBundle - does not send when recipient has no devices`() = runTest {
        prepareOwnDevice()
        prepareEncryptedMedia()

        tm.writeTransaction { keyStore.updateDeviceKeys(recipientUserId) { mapOf() } }

        cut.shareRoomKeyBundle(roomId, recipientUserId).getOrThrow()

        mediaService.uploadMediaCalled.value shouldBe null
        olmEncryptionService.encryptOlmCalled shouldBe null
    }

    @Test
    fun `shareRoomKeyBundle - ignores devices for which olm encryption fails`() = runTest {
        prepareOwnDevice()
        prepareRecipientDevice()
        prepareEncryptedMedia()

        olmEncryptionService.returnEncryptOlm =
            Result.failure(OlmEncryptionService.EncryptOlmError.NoOlmSupported("no idea why"))

        var sendCalled = false
        apiConfig.endpoints { matrixJsonEndpoint(SendToDevice("m.room.encrypted", "*")) { sendCalled = true } }

        cut.shareRoomKeyBundle(roomId, recipientUserId).getOrThrow()

        sendCalled shouldBe false
    }

    @Test
    fun `shareRoomKeyBundle - propagates network encryption errors`() = runTest {
        prepareOwnDevice()
        prepareRecipientDevice()
        prepareEncryptedMedia()

        val error = OlmEncryptionService.EncryptOlmError.NetworkError(IllegalStateException("network error"))
        olmEncryptionService.returnEncryptOlm = Result.failure(error)

        shouldThrow<Exception> { cut.shareRoomKeyBundle(roomId, recipientUserId).getOrThrow() } shouldBe error
    }

    @Test
    fun `shareRoomKeyBundle - propagates crypto driver encryption errors`() = runTest {
        prepareOwnDevice()
        prepareRecipientDevice()
        prepareEncryptedMedia()

        val error =
            OlmEncryptionService.EncryptOlmError.CryptoDriverError(
                CryptoDriverException(IllegalStateException("crypto error"))
            )
        olmEncryptionService.returnEncryptOlm = Result.failure(error)

        shouldThrow<Exception> { cut.shareRoomKeyBundle(roomId, recipientUserId).getOrThrow() } shouldBe error
    }

    @Test
    fun `shareRoomKeyBundle - propagates media upload errors`() = runTest {
        prepareOwnDevice()
        prepareRecipientDevice()
        prepareEncryptedMedia()

        val error = IllegalStateException("upload failed")
        mediaService.returnUploadMedia = Result.failure(error)

        shouldThrow<Exception> { cut.shareRoomKeyBundle(roomId, recipientUserId).getOrThrow() } shouldBe error
    }

    @Test
    fun `shareRoomKeyBundle - waits until preconditions met`() = runTest {
        prepareEncryptedMedia()
        prepareRecipientDevice()
        prepareOlmEncryption()
        dehydratedDeviceService.pendingRehydration.value = true

        tm.writeTransaction {
            keyStore.updateDeviceKeys(ownUserId) {
                mapOf(
                    ownDeviceId to
                        StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(ownUserId, ownDeviceId, setOf(), keysOf()), mapOf()),
                            KeySignatureTrustLevel.Valid(true),
                        )
                )
            }
        }

        val job = backgroundScope.launch { cut.shareRoomKeyBundle(roomId, recipientUserId) }
        delay(1.seconds)
        job.isActive shouldBe true

        currentSyncState.value = SyncState.RUNNING
        delay(1.seconds)
        job.isActive shouldBe true

        tm.writeTransaction {
            keyStore.updateDeviceKeys(ownUserId) {
                mapOf(
                    ownDeviceId to
                        StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(ownUserId, ownDeviceId, setOf(), keysOf()), mapOf()),
                            CrossSigned(true),
                        )
                )
            }
        }
        delay(1.seconds)
        job.isActive shouldBe true

        dehydratedDeviceService.pendingRehydration.value = false
        delay(1.seconds)
        job.isActive shouldBe false

        job.join()
    }

    private suspend fun prepareOwnDevice() {
        tm.writeTransaction {
            keyStore.updateDeviceKeys(ownUserId) {
                mapOf(
                    ownDeviceId to
                        StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(ownUserId, ownDeviceId, setOf(), keysOf()), mapOf()),
                            CrossSigned(true),
                        )
                )
            }
        }
    }

    private suspend fun prepareRecipientDevice() {
        tm.writeTransaction {
            keyStore.updateDeviceKeys(recipientUserId) {
                mapOf(
                    recipientDeviceId to
                        StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(recipientUserId, recipientDeviceId, setOf(), keysOf()),
                                mapOf(),
                            ),
                            CrossSigned(true),
                        )
                )
            }
        }
    }

    private fun prepareEncryptedMedia() {
        mediaService.returnPrepareUploadEncryptedMedia +=
            EncryptedFile(
                url = "mxc://example.org/encrypted-key-bundle",
                key = EncryptedFile.JWK(key = "key"),
                initialisationVector = "iv",
                hashes = mapOf("sha256" to "hash"),
            )
        mediaService.returnUploadMedia = Result.success("mxc://example.org/uploaded-key-bundle")
    }

    private fun prepareOlmEncryption() {
        olmEncryptionService.returnEncryptOlm =
            Result.success(OlmEncryptedToDeviceEventContent(ciphertext = mapOf(), senderKey = senderKey))
    }
}
