package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.mocks.RoomEventEncryptionServiceMock
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService.DecryptMegolmError
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList

class RoomEventEncryptionServiceTest : TrixnityBaseTest() {
    private val room = simpleRoom.roomId
    private val alice = UserId("alice", "server")
    private val roomStore = getInMemoryRoomStore()
    private val encryptedEvent =
        MessageEvent(
            MegolmEncryptedMessageEventContent(MegolmMessageValue("ciphertext"), sessionId = "SESSION"),
            EventId("\$event1"),
            alice,
            room,
            1234,
        )
    private val unencryptedEvent =
        MessageEvent(RoomMessageEventContent.TextBased.Text("plain"), EventId("\$event2"), alice, room, 1234)

    @Test
    fun `decrypt » emit null when list is empty`() = runTest {
        emptyList<RoomEventEncryptionService>().decrypt(encryptedEvent).toList() shouldBe listOf(null)
    }

    @Test
    fun `decrypt » emit null when no service supports event`() = runTest {
        val service1 = RoomEventEncryptionServiceMock().apply { returnDecrypt = null }
        val service2 = UnencryptedRoomEventEncryptionService(roomStore)

        listOf(service1, service2).decrypt(encryptedEvent).toList() shouldBe listOf(null)
    }

    @Test
    fun `decrypt » combine emissions across services`() = runTest {
        val expectedContent = RoomMessageEventContent.TextBased.Text("decrypted")
        val error = RoomEventEncryptionServiceError(DecryptMegolmError.MegolmKeyNotFound())
        val unencryptedService = UnencryptedRoomEventEncryptionService(roomStore)
        val megolmService =
            RoomEventEncryptionServiceMock().apply {
                returnDecryptFlow = {
                    emit(Result.failure(error))
                    delay(20.milliseconds)
                    emit(Result.success(expectedContent))
                }
            }

        listOf(unencryptedService, megolmService).decrypt(encryptedEvent).toList() shouldBe
            listOf(Result.failure(error), Result.success(expectedContent))
    }

    @Test
    fun `UnencryptedRoomEventEncryptionService » decrypt unencrypted and ignore encrypted`() = runTest {
        val cut = UnencryptedRoomEventEncryptionService(roomStore)

        cut.decrypt(unencryptedEvent).toList() shouldBe listOf(Result.success(unencryptedEvent.content))
        cut.decrypt(encryptedEvent).toList() shouldBe listOf(null)
    }
}
