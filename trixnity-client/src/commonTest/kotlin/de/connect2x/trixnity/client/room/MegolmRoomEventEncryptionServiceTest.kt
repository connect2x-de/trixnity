package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.mocks.KeyBackupServiceMock
import de.connect2x.trixnity.client.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.OutgoingRoomKeyRequestEventHandlerMock
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeysBackupVersionResponse
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.model.keys.RoomKeyBackupAuthData
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.DecryptMegolmError
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptMegolmError
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MegolmRoomEventEncryptionServiceTest : TrixnityBaseTest() {
    private val alice = UserId("alice", "server")
    private val room = simpleRoom.roomId

    private val roomStore = getInMemoryRoomStore {
        update(room) { simpleRoom.copy(encrypted = true) }
    }

    private val roomStateStore = getInMemoryRoomStateStore {
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

    private val olmCryptoStore = getInMemoryOlmStore()

    private val keyBackupServiceMock = KeyBackupServiceMock().apply {
        version.value = GetRoomKeysBackupVersionResponse.V1(
            RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(Curve25519KeyValue("")),
            1, "", ""
        )
    }
    private val olmEncryptionServiceMock = OlmEncryptionServiceMock()
    private val outgoingRoomKeyRequestEventHandlerMock = OutgoingRoomKeyRequestEventHandlerMock()

    private val cut = MegolmRoomEventEncryptionService(
        roomStore = roomStore,
        loadMembersService = { _, _ -> },
        roomStateStore = roomStateStore,
        olmCryptoStore = olmCryptoStore,
        keyBackupService = keyBackupServiceMock,
        outgoingRoomKeyRequestEventHandler = outgoingRoomKeyRequestEventHandlerMock,
        olmEncryptionService = olmEncryptionServiceMock
    )

    private val session = "SESSION"
    private val senderKey = Key.Curve25519Key(null, "senderKey")
    private val storedSession = StoredInboundMegolmSession(
        senderKey.value, Ed25519KeyValue("ed"), session, room, 1, hasBeenBackedUp = false, isTrusted = false,
        forwardingCurve25519KeyChain = listOf(), pickled = "pickle"
    )
    private val encryptedEvent = MessageEvent(
        MegolmEncryptedMessageEventContent(MegolmMessageValue("cipher cipher"), sessionId = session),
        EventId("$1event"),
        alice,
        room,
        1234
    )

    @Test
    fun `encrypt » not wrap unexpected exception`() = runTest {
        val exception = RuntimeException("unexpected")
        olmEncryptionServiceMock.returnEncryptMegolm = Result.failure(exception)

        val result = cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), room)
        result?.isFailure shouldBe true
        result?.exceptionOrNull() shouldBe exception
    }

    @Test
    fun `encrypt » do not wrap EncryptMegolmError`() = runTest {
        val exception = EncryptMegolmError.OlmLibraryError(CryptoDriverException(RuntimeException("failed")))
        olmEncryptionServiceMock.returnEncryptMegolm = Result.failure(exception)

        val result = cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), room)
        result?.isFailure shouldBe true
        result?.exceptionOrNull() shouldBe RoomEventEncryptionServiceError(exception)
    }

    @Test
    fun `decrypt » not wrap unexpected exception`() = runTest {
        val exception = RuntimeException("unexpected")
        olmEncryptionServiceMock.returnDecryptMegolm.add(Result.failure(exception))
        olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }

        val result = cut.decrypt(encryptedEvent)
        result?.isFailure shouldBe true
        result?.exceptionOrNull() shouldBe exception
    }

    @Test
    fun `decrypt » wrap DecryptMegolmError`() = runTest {
        val exception = DecryptMegolmError.ValidationFailed("failed")
        olmEncryptionServiceMock.returnDecryptMegolm.add(Result.failure(exception))
        olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }

        val result = cut.decrypt(encryptedEvent)
        result?.isFailure shouldBe true
        result?.exceptionOrNull() shouldBe RoomEventEncryptionServiceError(exception)
    }
}
