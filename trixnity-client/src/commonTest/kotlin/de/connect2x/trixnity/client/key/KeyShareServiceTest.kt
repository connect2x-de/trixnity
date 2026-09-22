package de.connect2x.trixnity.client.key

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.getInMemoryAccountStore
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.media.InMemoryPlatformMedia
import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.DehydratedDeviceServiceMock
import de.connect2x.trixnity.client.mocks.KeyBackupServiceMock
import de.connect2x.trixnity.client.mocks.MediaServiceMock
import de.connect2x.trixnity.client.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.OlmEventHandlerMock
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel.CrossSigned
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.StoredRoomKeyBundles
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.DownloadLimitExceededException
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.user.SendToDevice
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData
import de.connect2x.trixnity.core.model.events.m.RoomKeyBundleEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
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
import de.connect2x.trixnity.crypto.olm.InboundMegolmSessionSource
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.toByteArray
import de.connect2x.trixnity.utils.toByteArrayFlow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KeyShareServiceTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val cryptoDriver = VodozemacCryptoDriver

    private val ownUserId = UserId("own", "server")
    private val ownDeviceId = "own_device"
    private val alice = UserId("alice", "server")
    private val aliceDevice = "alice_device"
    private val bob = UserId("bob", "server")
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
                olmEventHandler = OlmEventHandlerMock(),
                api = api,
                currentSyncState = CurrentSyncState(currentSyncState),
                cryptoDriver = cryptoDriver,
                json = json,
                clock = testScope.testClock,
                userInfo = UserInfo(ownUserId, ownDeviceId, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
                tm = tm,
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
                senderSigningKey = Ed25519KeyValue("edKey"),
                source = InboundMegolmSessionSource.Creator,
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
                senderSigningKey = Ed25519KeyValue("edKey"),
                source = InboundMegolmSessionSource.Creator,
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

        cut.shareRoomKeyBundle(roomId, alice).getOrThrow()

        sentEvents shouldBe
            mapOf(
                alice to
                    mapOf(aliceDevice to OlmEncryptedToDeviceEventContent(ciphertext = mapOf(), senderKey = senderKey))
            )

        mediaService.uploadMediaCalled.value shouldBe "mxc://example.org/encrypted-key-bundle"
        olmEncryptionService.encryptOlmCalled?.second shouldBe alice
        olmEncryptionService.encryptOlmCalled?.third shouldBe aliceDevice

        val eventContent = olmEncryptionService.encryptOlmCalled?.first as RoomKeyBundleEventContent
        eventContent.roomId shouldBe roomId
        eventContent.file.url shouldBe "mxc://example.org/uploaded-key-bundle"
    }

    @Test
    fun `shareRoomKeyBundle - does not send when recipient has no cross signed devices`() = runTest {
        prepareOwnDevice()
        prepareEncryptedMedia()

        tm.writeTransaction {
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to
                        StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                            KeySignatureTrustLevel.Valid(false),
                        )
                )
            }
        }

        cut.shareRoomKeyBundle(roomId, alice).getOrThrow()

        mediaService.uploadMediaCalled.value shouldBe null
        olmEncryptionService.encryptOlmCalled shouldBe null
    }

    @Test
    fun `shareRoomKeyBundle - does not send when recipient has no devices`() = runTest {
        prepareOwnDevice()
        prepareEncryptedMedia()

        tm.writeTransaction { keyStore.updateDeviceKeys(alice) { mapOf() } }

        cut.shareRoomKeyBundle(roomId, alice).getOrThrow()

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

        cut.shareRoomKeyBundle(roomId, alice).getOrThrow()

        sendCalled shouldBe false
    }

    @Test
    fun `shareRoomKeyBundle - propagates network encryption errors`() = runTest {
        prepareOwnDevice()
        prepareRecipientDevice()
        prepareEncryptedMedia()

        val error = OlmEncryptionService.EncryptOlmError.NetworkError(IllegalStateException("network error"))
        olmEncryptionService.returnEncryptOlm = Result.failure(error)

        shouldThrow<Exception> { cut.shareRoomKeyBundle(roomId, alice).getOrThrow() } shouldBe error
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

        shouldThrow<Exception> { cut.shareRoomKeyBundle(roomId, alice).getOrThrow() } shouldBe error
    }

    @Test
    fun `shareRoomKeyBundle - propagates media upload errors`() = runTest {
        prepareOwnDevice()
        prepareRecipientDevice()
        prepareEncryptedMedia()

        val error = IllegalStateException("upload failed")
        mediaService.returnUploadMedia = Result.failure(error)

        shouldThrow<Exception> { cut.shareRoomKeyBundle(roomId, alice).getOrThrow() } shouldBe error
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

        val job = backgroundScope.launch { cut.shareRoomKeyBundle(roomId, alice) }
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

    @Test
    fun `applyRoomKeyBundle - does remove key bundle when expired`() = runTest {
        val storedRoomKeyBundles =
            StoredRoomKeyBundles(
                roomId,
                alice,
                testClock.now(),
                setOf(
                    StoredRoomKeyBundles.Bundle(
                        senderUserId = alice,
                        senderDeviceId = aliceDevice,
                        content =
                            RoomKeyBundleEventContent(
                                roomId = roomId,
                                file =
                                    EncryptedFile(
                                        url = "mxc://example.org/key-bundle",
                                        key = EncryptedFile.JWK(key = "key"),
                                        initialisationVector = "iv",
                                        hashes = mapOf("sha256" to "hash"),
                                    ),
                            ),
                    )
                ),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }
        delay(1.seconds)
        cut.applyRoomKeyBundle(storedRoomKeyBundles).getOrThrow()
        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    @Test
    fun `applyRoomKeyBundle - does ignore key bundles from not accepted users`() = runTest {
        val storedRoomKeyBundles =
            StoredRoomKeyBundles(
                roomId,
                bob,
                testClock.now() + 1.days,
                setOf(
                    StoredRoomKeyBundles.Bundle(
                        senderUserId = alice,
                        senderDeviceId = aliceDevice,
                        content =
                            RoomKeyBundleEventContent(
                                roomId = roomId,
                                file =
                                    EncryptedFile(
                                        url = "mxc://example.org/key-bundle",
                                        key = EncryptedFile.JWK(key = "key"),
                                        initialisationVector = "iv",
                                        hashes = mapOf("sha256" to "hash"),
                                    ),
                            ),
                    )
                ),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }
        cut.applyRoomKeyBundle(storedRoomKeyBundles).getOrThrow()
        keyStore.getRoomKeyBundles(roomId) shouldBe storedRoomKeyBundles
        olmCryptoStore.getInboundMegolmSessions(roomId).flatten().first().shouldBeEmpty()
    }

    @Test
    fun `applyRoomKeyBundle - imports room keys and removes bundle`() = runTest {
        val outboundSession = cryptoDriver.megolm.groupSession()
        outboundSession.encrypt("test")

        val roomKeyBundle =
            RoomKeyBundle(
                roomKeys =
                    listOf(
                        RoomKeyBundle.HistoricRoomKey(
                            roomId = roomId,
                            senderKey = senderKey,
                            senderClaimedKeys = Keys(Ed25519Key(null, Ed25519KeyValue("edKey"))),
                            sessionId = outboundSession.sessionId,
                            sessionKey =
                                ExportedSessionKeyValue.of(
                                    cryptoDriver.megolm
                                        .inboundGroupSession(outboundSession.sessionKey)
                                        .exportAtFirstKnownIndex()
                                ),
                        )
                    )
            )

        val storedBundle =
            StoredRoomKeyBundles.Bundle(
                senderUserId = alice,
                senderDeviceId = aliceDevice,
                content =
                    RoomKeyBundleEventContent(
                        roomId = roomId,
                        file =
                            EncryptedFile(
                                url = "mxc://example.org/key-bundle",
                                key = EncryptedFile.JWK(key = "key"),
                                initialisationVector = "iv",
                                hashes = mapOf("sha256" to "hash"),
                            ),
                    ),
            )

        val storedRoomKeyBundles =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = testClock.now() + 1.days,
                bundles = setOf(storedBundle),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }

        mediaService.returnGetEncryptedMedia =
            Result.success(
                ByteArrayFlowPlatformMedia(json.encodeToString(roomKeyBundle).encodeToByteArray().toByteArrayFlow())
            )

        cut.applyRoomKeyBundle(storedRoomKeyBundles).getOrThrow()

        keyStore.getRoomKeyBundles(roomId) shouldBe null

        val storedSession =
            olmCryptoStore
                .getInboundMegolmSessions(roomId)
                .flatten()
                .first()[outboundSession.sessionId]
                .shouldNotBeNull()

        storedSession.senderKey shouldBe senderKey
        storedSession.sessionId shouldBe outboundSession.sessionId
        storedSession.roomId shouldBe roomId
        storedSession.senderSigningKey shouldBe Ed25519KeyValue("edKey")
        storedSession.sharedHistory shouldBe true
        storedSession.firstKnownIndex shouldBe 1
        storedSession.hasBeenBackedUp shouldBe false
        storedSession.source shouldBe
            InboundMegolmSessionSource.KeyBundle(setOf(InboundMegolmSessionSource.KeyBundle.Sender(alice, aliceDevice)))
        cryptoDriver.megolm.inboundGroupSession.fromPickle(storedSession.pickled).sessionId shouldBe
            outboundSession.sessionId
    }

    @Test
    fun `applyRoomKeyBundle - not replaces existing session when imported session has newer first known index`() =
        runTest {
            val outboundSession = cryptoDriver.megolm.groupSession()
            val inboundSession = cryptoDriver.megolm.inboundGroupSession(outboundSession.sessionKey)
            outboundSession.encrypt("test")

            val roomKeyBundle =
                RoomKeyBundle(
                    roomKeys =
                        listOf(
                            RoomKeyBundle.HistoricRoomKey(
                                roomId = roomId,
                                senderKey = senderKey,
                                senderClaimedKeys = Keys(Ed25519Key(null, Ed25519KeyValue("edKey"))),
                                sessionId = outboundSession.sessionId,
                                sessionKey =
                                    ExportedSessionKeyValue.of(
                                        cryptoDriver.megolm
                                            .inboundGroupSession(outboundSession.sessionKey)
                                            .exportAtFirstKnownIndex()
                                    ),
                            )
                        )
                )

            val storedBundle =
                StoredRoomKeyBundles.Bundle(
                    senderUserId = alice,
                    senderDeviceId = aliceDevice,
                    content =
                        RoomKeyBundleEventContent(
                            roomId = roomId,
                            file =
                                EncryptedFile(
                                    url = "mxc://example.org/key-bundle",
                                    key = EncryptedFile.JWK(key = "key"),
                                    initialisationVector = "iv",
                                    hashes = mapOf("sha256" to "hash"),
                                ),
                        ),
                )

            val storedRoomKeyBundles =
                StoredRoomKeyBundles(
                    roomId = roomId,
                    acceptFrom = alice,
                    acceptUntil = testClock.now() + 1.days,
                    bundles = setOf(storedBundle),
                )
            tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }

            mediaService.returnGetEncryptedMedia =
                Result.success(
                    ByteArrayFlowPlatformMedia(json.encodeToString(roomKeyBundle).encodeToByteArray().toByteArrayFlow())
                )

            val existingStoredInboundMegolmSession =
                StoredInboundMegolmSession(
                    senderKey = senderKey,
                    sessionId = outboundSession.sessionId,
                    roomId = roomId,
                    firstKnownIndex = 0,
                    hasBeenBackedUp = true,
                    senderSigningKey = Ed25519KeyValue("edKey"),
                    source = InboundMegolmSessionSource.Creator,
                    pickled = inboundSession.pickle(),
                    sharedHistory = true,
                )
            tm.writeTransaction {
                olmCryptoStore.updateInboundMegolmSession(outboundSession.sessionId, roomId) {
                    existingStoredInboundMegolmSession
                }
            }

            cut.applyRoomKeyBundle(storedRoomKeyBundles).getOrThrow()

            keyStore.getRoomKeyBundles(roomId) shouldBe null

            olmCryptoStore.getInboundMegolmSessions(roomId).flatten().first()[outboundSession.sessionId] shouldBe
                existingStoredInboundMegolmSession
        }

    @Test
    fun `applyRoomKeyBundle - removes bundle when media is not found`() = runTest {
        val storedBundle =
            StoredRoomKeyBundles.Bundle(
                alice,
                aliceDevice,
                RoomKeyBundleEventContent(
                    roomId,
                    EncryptedFile(
                        url = "mxc://example.org/missing",
                        key = EncryptedFile.JWK(key = "key"),
                        initialisationVector = "iv",
                        hashes = mapOf("sha256" to "hash"),
                    ),
                ),
            )
        val storedRoomKeyBundles =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = testClock.now() + 1.days,
                bundles = setOf(storedBundle),
            )

        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }

        mediaService.returnGetEncryptedMedia =
            Result.failure(MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound("has been removed")))

        cut.applyRoomKeyBundle(storedRoomKeyBundles).getOrThrow()

        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    @Test
    fun `applyRoomKeyBundle - removes bundle when media download exceeds limit`() = runTest {
        val storedBundle =
            StoredRoomKeyBundles.Bundle(
                alice,
                aliceDevice,
                RoomKeyBundleEventContent(
                    roomId,
                    EncryptedFile(
                        url = "mxc://example.org/too-large",
                        key = EncryptedFile.JWK(key = "key"),
                        initialisationVector = "iv",
                        hashes = mapOf("sha256" to "hash"),
                    ),
                ),
            )
        val storedRoomKeyBundles =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = testClock.now() + 1.days,
                bundles = setOf(storedBundle),
            )

        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }

        mediaService.returnGetEncryptedMedia = Result.failure(DownloadLimitExceededException(24))

        cut.applyRoomKeyBundle(storedRoomKeyBundles).getOrThrow()

        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    @Test
    fun `applyRoomKeyBundle - removes bundle when json is invalid`() = runTest {
        val storedBundle =
            StoredRoomKeyBundles.Bundle(
                alice,
                aliceDevice,
                RoomKeyBundleEventContent(
                    roomId,
                    EncryptedFile(
                        url = "mxc://example.org/invalid",
                        key = EncryptedFile.JWK(key = "key"),
                        initialisationVector = "iv",
                        hashes = mapOf("sha256" to "hash"),
                    ),
                ),
            )

        val storedRoomKeyBundles =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = testClock.now() + 1.days,
                bundles = setOf(storedBundle),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }

        mediaService.returnGetEncryptedMedia =
            Result.success(ByteArrayFlowPlatformMedia("this is not json".encodeToByteArray().toByteArrayFlow()))

        cut.applyRoomKeyBundle(storedRoomKeyBundles).getOrThrow()

        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    @Test
    fun `applyRoomKeyBundle - propagates unexpected media errors`() = runTest {
        val storedBundle =
            StoredRoomKeyBundles.Bundle(
                alice,
                aliceDevice,
                RoomKeyBundleEventContent(
                    roomId,
                    EncryptedFile(
                        url = "mxc://example.org/error",
                        key = EncryptedFile.JWK(key = "key"),
                        initialisationVector = "iv",
                        hashes = mapOf("sha256" to "hash"),
                    ),
                ),
            )

        val storedRoomKeyBundles =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = testClock.now() + 1.days,
                bundles = setOf(storedBundle),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { storedRoomKeyBundles } }

        val error = IllegalStateException("download failed")
        mediaService.returnGetEncryptedMedia = Result.failure(error)

        cut.applyRoomKeyBundle(storedRoomKeyBundles).exceptionOrNull() shouldBe error
    }

    @Test
    fun `handleMembershipChanges - invite creates room key bundle state`() = runTest {
        cut.handleMembershipChanges(listOf(memberEvent(roomId, alice, Membership.INVITE)))

        keyStore.getRoomKeyBundles(roomId) shouldBe
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = Instant.DISTANT_FUTURE,
                bundles = setOf(),
            )
    }

    @Test
    fun `handleMembershipChanges - invite updates existing room key bundle state`() = runTest {
        val existing =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = bob,
                acceptUntil = Instant.DISTANT_FUTURE,
                bundles = setOf(),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { existing } }

        cut.handleMembershipChanges(listOf(memberEvent(roomId, alice, Membership.INVITE)))

        keyStore.getRoomKeyBundles(roomId) shouldBe existing.copy(acceptFrom = alice)
    }

    @Test
    fun `handleMembershipChanges - join after invite sets accept until`() = runTest {
        val originTimestamp = 1_000L
        tm.writeTransaction {
            keyStore.updateRoomKeyBundles(roomId) {
                StoredRoomKeyBundles(
                    roomId = roomId,
                    acceptFrom = alice,
                    acceptUntil = Instant.DISTANT_FUTURE,
                    bundles = setOf(),
                )
            }
        }

        cut.handleMembershipChanges(
            listOf(
                memberEvent(
                    roomId = roomId,
                    sender = alice,
                    membership = Membership.JOIN,
                    previousMembership = Membership.INVITE,
                    originTimestamp = originTimestamp,
                )
            )
        )

        keyStore.getRoomKeyBundles(roomId)?.acceptUntil shouldBe
            Instant.fromEpochMilliseconds(originTimestamp).plus(1.days)
    }

    @Test
    fun `handleMembershipChanges - join without previous invite removes room key bundle state`() = runTest {
        tm.writeTransaction {
            keyStore.updateRoomKeyBundles(roomId) {
                StoredRoomKeyBundles(
                    roomId = roomId,
                    acceptFrom = alice,
                    acceptUntil = Instant.DISTANT_FUTURE,
                    bundles = setOf(),
                )
            }
        }

        cut.handleMembershipChanges(
            listOf(memberEvent(roomId, alice, Membership.JOIN, previousMembership = Membership.LEAVE))
        )

        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    @Test
    fun `handleMembershipChanges - join after join leaves room key bundle state unchanged`() = runTest {
        val existing =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = Instant.DISTANT_FUTURE,
                bundles = setOf(),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { existing } }

        cut.handleMembershipChanges(
            listOf(memberEvent(roomId, alice, Membership.JOIN, previousMembership = Membership.JOIN))
        )

        keyStore.getRoomKeyBundles(roomId) shouldBe existing
    }

    @Test
    fun `handleMembershipChanges - knock does not change room key bundle state`() = runTest {
        val existing =
            StoredRoomKeyBundles(
                roomId = roomId,
                acceptFrom = alice,
                acceptUntil = Instant.DISTANT_FUTURE,
                bundles = setOf(),
            )
        tm.writeTransaction { keyStore.updateRoomKeyBundles(roomId) { existing } }

        cut.handleMembershipChanges(listOf(memberEvent(roomId, alice, Membership.KNOCK)))

        keyStore.getRoomKeyBundles(roomId) shouldBe existing
    }

    @Test
    fun `handleMembershipChanges - leave removes room key bundle state`() = runTest {
        tm.writeTransaction {
            keyStore.updateRoomKeyBundles(roomId) {
                StoredRoomKeyBundles(
                    roomId = roomId,
                    acceptFrom = alice,
                    acceptUntil = Instant.DISTANT_FUTURE,
                    bundles = setOf(),
                )
            }
        }

        cut.handleMembershipChanges(listOf(memberEvent(roomId, alice, Membership.LEAVE)))

        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    @Test
    fun `handleMembershipChanges - ban removes room key bundle state`() = runTest {
        tm.writeTransaction {
            keyStore.updateRoomKeyBundles(roomId) {
                StoredRoomKeyBundles(
                    roomId = roomId,
                    acceptFrom = alice,
                    acceptUntil = Instant.DISTANT_FUTURE,
                    bundles = setOf(),
                )
            }
        }

        cut.handleMembershipChanges(listOf(memberEvent(roomId, alice, Membership.BAN)))

        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    @Test
    fun `handleMembershipChanges - ignores membership events for other users`() = runTest {
        cut.handleMembershipChanges(listOf(memberEvent(roomId, alice, Membership.INVITE, stateKey = alice.full)))

        keyStore.getRoomKeyBundles(roomId) shouldBe null
    }

    private fun memberEvent(
        roomId: RoomId,
        sender: UserId,
        membership: Membership,
        stateKey: String = ownUserId.full,
        previousMembership: Membership? = null,
        originTimestamp: Long = 1_000L,
    ): ClientEvent.StateBaseEvent<MemberEventContent> =
        ClientEvent.RoomEvent.StateEvent(
            content = MemberEventContent(membership = membership),
            id = EventId("blub"),
            sender = sender,
            roomId = roomId,
            originTimestamp = originTimestamp,
            unsigned =
                previousMembership?.let {
                    UnsignedRoomEventData.UnsignedStateEventData(previousContent = MemberEventContent(membership = it))
                },
            stateKey = stateKey,
        )

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
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to
                        StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
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

    private class ByteArrayFlowPlatformMedia(private val delegate: ByteArrayFlow) :
        InMemoryPlatformMedia, ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): PlatformMedia =
            ByteArrayFlowPlatformMedia(delegate.let(transformer))

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?,
        ): ByteArray? = if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()

        override suspend fun getTemporaryFile(): Result<PlatformMedia.TemporaryFile> =
            Result.success(
                object : PlatformMedia.TemporaryFile {
                    override suspend fun delete() {}
                }
            )
    }
}
