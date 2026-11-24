package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.mocks.KeyBackupServiceMock
import de.connect2x.trixnity.client.mocks.MegolmEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.OutgoingRoomKeyRequestEventHandlerMock
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeysBackupVersionResponse
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.PlaintextMegolmEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.model.keys.RoomKeyBackupAuthData
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import de.connect2x.trixnity.crypto.olm.InboundMegolmSessionSource
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService.DecryptMegolmError
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

class MegolmRoomEventDecryptionServiceTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val alice = UserId("alice", "server")
    private val room = simpleRoom.roomId

    private val roomStore = getInMemoryRoomStore {
        tm.writeTransaction { update(room) { simpleRoom.copy(encrypted = true) } }
    }

    private val roomStateStore = getInMemoryRoomStateStore {
        tm.writeTransaction {
            save(
                ClientEvent.RoomEvent.StateEvent(
                    EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm),
                    EventId("enc_state"),
                    alice,
                    room,
                    1234,
                    stateKey = "",
                )
            )
        }
    }

    private val olmCryptoStore = getInMemoryOlmStore()

    private val keyBackupServiceMock =
        KeyBackupServiceMock().apply {
            version.value =
                GetRoomKeysBackupVersionResponse.V1(
                    RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(Curve25519KeyValue("")),
                    1,
                    "",
                    "",
                )
        }
    private val megolmEncyptionServiceMock =
        MegolmEncryptionServiceMock().apply {
            scheduleSetup {
                returnDecryptMegolm.clear()
                onDecryptMegolm = null
                returnEncryptMegolm = null
            }
        }
    private val outgoingRoomKeyRequestEventHandlerMock = OutgoingRoomKeyRequestEventHandlerMock()

    private val cut =
        MegolmRoomEventEncryptionService(
            roomStore = roomStore,
            loadMembersService = { _, _ -> },
            roomStateStore = roomStateStore,
            olmCryptoStore = olmCryptoStore,
            keyBackupService = keyBackupServiceMock,
            outgoingRoomKeyRequestEventHandler = outgoingRoomKeyRequestEventHandlerMock,
            megolmEncryptionService = megolmEncyptionServiceMock,
        )

    private val session = "SESSION"
    private val senderKey = Key.Curve25519Key(null, "senderKey")
    private val storedSession =
        StoredInboundMegolmSession.Key(
            senderKey = senderKey.value,
            senderSigningKey = Ed25519KeyValue("ed"),
            sessionId = session,
            roomId = room,
            firstKnownIndex = 1,
            hasBeenBackedUp = false,
            source = InboundMegolmSessionSource.Creator,
            sharedHistory = true,
            pickled = "pickle",
        )
    private val encryptedEvent =
        MessageEvent(
            MegolmEncryptedMessageEventContent(MegolmMessageValue("cipher cipher"), sessionId = session),
            EventId("$1event"),
            alice,
            room,
            1234,
        )
    private val expectedDecryptedEvent = PlaintextMegolmEvent(RoomMessageEventContent.TextBased.Text("decrypted"), room)

    @Test
    fun `encrypt » return null when room does not exist`() = runTest {
        tm.writeTransaction {
            roomStateStore.save(
                ClientEvent.RoomEvent.StateEvent(
                    EncryptionEventContent(algorithm = EncryptionAlgorithm.Unknown("super-duper-crypto")),
                    EventId("enc_state"),
                    alice,
                    room,
                    1234,
                    stateKey = "",
                )
            )
        }
        cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), RoomId("unknown")) shouldBe null
    }

    @Test
    fun `encrypt » only encrypt in room with megolm algorithm`() = runTest {
        tm.writeTransaction {
            roomStateStore.save(
                ClientEvent.RoomEvent.StateEvent(
                    EncryptionEventContent(algorithm = EncryptionAlgorithm.Unknown("super-duper-crypto")),
                    EventId("enc_state"),
                    alice,
                    room,
                    1234,
                    stateKey = "",
                )
            )
        }
        cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), room) shouldBe null
    }

    @Test
    fun `encrypt » encrypt`() = runTest {
        val encryptedEvent = MegolmEncryptedMessageEventContent(MegolmMessageValue("cipher"), sessionId = "sessionId")
        megolmEncyptionServiceMock.returnEncryptMegolm = Result.success(encryptedEvent)
        cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), room) shouldBe Result.success(encryptedEvent)
    }

    @Test
    fun `decrypt » return null when unsupported`() = runTest {
        cut.decrypt(
                MessageEvent(
                    RoomMessageEventContent.TextBased.Text("unsupported"),
                    EventId("$1event"),
                    alice,
                    room,
                    1234,
                )
            )
            .first() shouldBe null
    }

    @Test
    fun `decrypt » only decrypt in room with megolm alroithm`() = runTest {
        tm.writeTransaction {
            roomStateStore.save(
                ClientEvent.RoomEvent.StateEvent(
                    EncryptionEventContent(algorithm = EncryptionAlgorithm.Unknown("super-duper-crypto")),
                    EventId("enc_state"),
                    alice,
                    room,
                    1234,
                    stateKey = "",
                )
            )
            megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
        }
        cut.decrypt(encryptedEvent).first() shouldBe null
    }

    @Test
    fun `decrypt » decrypt event`() = runTest {
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
        cut.decrypt(encryptedEvent).first().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
    }

    @Test
    fun `decrypt » handle error`() = runTest {
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.failure(DecryptMegolmError.ValidationFailed("")))
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
        cut.decrypt(encryptedEvent).first().shouldNotBeNull().exceptionOrNull() shouldBe
            RoomEventEncryptionServiceError(DecryptMegolmError.ValidationFailed(""))
    }

    @Test
    fun `decrypt » wait for olm session and ask key backup for it`() = runTest {
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.failure(DecryptMegolmError.MegolmKeyNotFound()))
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

        val result = async { cut.decrypt(encryptedEvent).toList() }
        delay(20.milliseconds)
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
        delay(20.milliseconds)
        val results = result.await()
        results.size shouldBe 2
        results[0]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause
            .shouldBeInstanceOf<DecryptMegolmError.MegolmKeyNotFound>()
        results[1].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        keyBackupServiceMock.loadMegolmSessionCalled.value.first() shouldBe Pair(room, session)
    }

    @Test
    fun `decrypt » wait for olm session and ask other device for it when key backup disabled`() = runTest {
        keyBackupServiceMock.version.value = null
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.failure(DecryptMegolmError.MegolmKeyNotFound()))
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

        val result = async { cut.decrypt(encryptedEvent).toList() }
        delay(20.milliseconds)
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
        delay(20.milliseconds)
        val results = result.await()
        results.size shouldBe 2
        results[0]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause
            .shouldBeInstanceOf<DecryptMegolmError.MegolmKeyNotFound>()
        results[1].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        outgoingRoomKeyRequestEventHandlerMock.requestRoomKeysCalled.value.first() shouldBe Pair(room, session)
    }

    @Test
    fun `decrypt » wait for olm session and ask key backup for it when existing session does not known the index`() =
        runTest {
            megolmEncyptionServiceMock.returnDecryptMegolm.add(
                Result.failure(DecryptMegolmError.MegolmKeyUnknownMessageIndex())
            )
            megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

            tm.writeTransaction {
                olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession.copy(firstKnownIndex = 4) }
            }
            val result = async { cut.decrypt(encryptedEvent).toList() }

            delay(20.milliseconds)
            tm.writeTransaction {
                olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession.copy(firstKnownIndex = 3) }
            }
            val results = result.await()
            results.size shouldBe 2
            results[0]
                .shouldNotBeNull()
                .exceptionOrNull()
                .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
                .cause
                .shouldBeInstanceOf<DecryptMegolmError.MegolmKeyUnknownMessageIndex>()
            results[1].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
            keyBackupServiceMock.loadMegolmSessionCalled.value.size shouldBe 1
            keyBackupServiceMock.loadMegolmSessionCalled.value.first() shouldBe Pair(room, session)
        }

    @Test
    fun `decrypt » handle non-retryable error without requesting key backup or room keys`() = runTest {
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.failure(DecryptMegolmError.ValidationFailed("")))
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
        cut.decrypt(encryptedEvent).first().shouldNotBeNull().exceptionOrNull() shouldBe
            RoomEventEncryptionServiceError(DecryptMegolmError.ValidationFailed(""))
        keyBackupServiceMock.loadMegolmSessionCalled.value.shouldBeEmpty()
        outgoingRoomKeyRequestEventHandlerMock.requestRoomKeysCalled.value.shouldBeEmpty()
    }

    @Test
    fun `decrypt » wait for olm session and ask key backup for it when key is withheld`() = runTest {
        val withheldSession =
            StoredInboundMegolmSession.Withheld(
                roomId = room,
                senderKey = senderKey.value,
                sessionId = session,
                source = InboundMegolmSessionSource.Creator,
                code = RoomKeyWithheldCode.Unverified,
                reason = "unverified",
            )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(
            Result.failure(DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified"))
        )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { withheldSession } }
        val result = async { cut.decrypt(encryptedEvent).toList() }

        delay(20.milliseconds)
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }

        val results = result.await()
        results.size shouldBe 2
        results[0]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause shouldBe DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified")
        results[1].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        keyBackupServiceMock.loadMegolmSessionCalled.value.first() shouldBe Pair(room, session)
    }

    @Test
    fun `decrypt » wait for olm session and ask other device for it when key is withheld and key backup disabled`() =
        runTest {
            keyBackupServiceMock.version.value = null
            val withheldSession =
                StoredInboundMegolmSession.Withheld(
                    roomId = room,
                    senderKey = senderKey.value,
                    sessionId = session,
                    source = InboundMegolmSessionSource.Creator,
                    code = RoomKeyWithheldCode.Unverified,
                    reason = "unverified",
                )
            megolmEncyptionServiceMock.returnDecryptMegolm.add(
                Result.failure(DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified"))
            )
            megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

            tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { withheldSession } }
            val result = async { cut.decrypt(encryptedEvent).toList() }

            delay(20.milliseconds)
            tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }

            val results = result.await()
            results.size shouldBe 2
            results[0]
                .shouldNotBeNull()
                .exceptionOrNull()
                .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
                .cause shouldBe DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified")
            results[1].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
            outgoingRoomKeyRequestEventHandlerMock.requestRoomKeysCalled.value.first() shouldBe Pair(room, session)
        }

    @Test
    fun `decrypt » emit withheld then decrypt when withheld arrives before key`() = runTest {
        val withheldSession =
            StoredInboundMegolmSession.Withheld(
                roomId = room,
                senderKey = senderKey.value,
                sessionId = session,
                source = InboundMegolmSessionSource.Creator,
                code = RoomKeyWithheldCode.Unauthorised,
                reason = "not allowed",
            )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.failure(DecryptMegolmError.MegolmKeyNotFound()))
        megolmEncyptionServiceMock.returnDecryptMegolm.add(
            Result.failure(DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unauthorised, "not allowed"))
        )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

        val collected = mutableListOf<Result<MessageEventContent>?>()
        val job = launch { cut.decrypt(encryptedEvent).collect { collected.add(it) } }

        delay(20.milliseconds)
        collected.size shouldBe 1
        collected[0]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause
            .shouldBeInstanceOf<DecryptMegolmError.MegolmKeyNotFound>()

        // Storing Withheld wakes up waitForInboundMegolmSession(allowWithheld = true) and emits Withheld
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { withheldSession } }
        delay(20.milliseconds)
        collected.size shouldBe 2
        collected[1]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause shouldBe DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unauthorised, "not allowed")
        job.isActive shouldBe true

        // Storing Key wakes up waitForInboundMegolmSession(allowWithheld = false) and finishes decryption
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
        job.join()
        collected.size shouldBe 3
        collected[2].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
    }

    @Test
    fun `decrypt » emit updated withheld error when withheld session changes before key arrives`() = runTest {
        val withheldSession1 =
            StoredInboundMegolmSession.Withheld(
                roomId = room,
                senderKey = senderKey.value,
                sessionId = session,
                source = InboundMegolmSessionSource.Creator,
                code = RoomKeyWithheldCode.Unverified,
                reason = "unverified",
            )
        val withheldSession2 = withheldSession1.copy(code = RoomKeyWithheldCode.Unauthorised, reason = "not allowed")
        megolmEncyptionServiceMock.returnDecryptMegolm.add(
            Result.failure(DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified"))
        )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(
            Result.failure(DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unauthorised, "not allowed"))
        )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { withheldSession1 } }
        val collected = mutableListOf<Result<MessageEventContent>?>()
        val job = launch { cut.decrypt(encryptedEvent).collect { collected.add(it) } }

        delay(20.milliseconds)
        collected.size shouldBe 1
        collected[0]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause shouldBe DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified")
        job.isActive shouldBe true

        // Updating Withheld wakes up waitForInboundMegolmSession(othenThan = withheldSession1)
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { withheldSession2 } }
        delay(20.milliseconds)
        collected.size shouldBe 2
        collected[1]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause shouldBe DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unauthorised, "not allowed")
        job.isActive shouldBe true

        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
        job.join()
        collected.size shouldBe 3
        collected[2].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
    }

    @Test
    fun `decrypt » retry immediately when session changes during decryptMegolm`() = runTest {
        val withheldSession1 =
            StoredInboundMegolmSession.Withheld(
                roomId = room,
                senderKey = senderKey.value,
                sessionId = session,
                source = InboundMegolmSessionSource.Creator,
                code = RoomKeyWithheldCode.Unverified,
                reason = "unverified",
            )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(
            Result.failure(DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified"))
        )
        megolmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))
        var firstAttempt = true
        megolmEncyptionServiceMock.onDecryptMegolm = {
            if (firstAttempt) {
                firstAttempt = false
                tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession } }
            }
        }

        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(session, room) { withheldSession1 } }
        val results = cut.decrypt(encryptedEvent).toList()

        results.size shouldBe 2
        results[0]
            .shouldNotBeNull()
            .exceptionOrNull()
            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
            .cause shouldBe DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified")
        results[1].shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        keyBackupServiceMock.loadMegolmSessionCalled.value.shouldBeEmpty()
    }
}
