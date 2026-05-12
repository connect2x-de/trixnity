package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import kotlinx.coroutines.delay
import kotlin.time.Duration

class RoomEventEncryptionServiceMock(dontReturnDecrypt: Boolean = false, private val useInput: Boolean = false) :
    RoomEventEncryptionService {
    var returnEncrypt: Result<MessageEventContent>? = null
    var returnEncryptList = mutableListOf<Result<MessageEventContent>>()
    var encryptCounter = 0
    override suspend fun encrypt(content: MessageEventContent, roomId: RoomId): Result<MessageEventContent>? {
        encryptCounter++
        val returnValue = returnEncryptList.takeIf { it.isNotEmpty() }?.removeFirst() ?: returnEncrypt
        return if (useInput) returnValue ?: Result.success(content)
        else returnValue
    }

    var returnDecrypt: Result<MessageEventContent>? = null
    var decryptCounter = 0
    var decryptDelay = if (dontReturnDecrypt) Duration.INFINITE else Duration.ZERO

    override suspend fun decrypt(event: RoomEvent.MessageEvent<*>): Result<MessageEventContent>? {
        decryptCounter++
        delay(decryptDelay)
        return if (useInput) returnDecrypt ?: Result.success(event.content)
        else returnDecrypt
    }
}
