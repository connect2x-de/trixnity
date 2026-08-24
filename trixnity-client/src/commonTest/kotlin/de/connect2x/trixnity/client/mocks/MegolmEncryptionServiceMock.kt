package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.DecryptedMegolmEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService

class MegolmEncryptionServiceMock : MegolmEncryptionService {

    var returnEncryptMegolm: Result<MegolmEncryptedMessageEventContent>? = null

    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent,
    ): Result<MegolmEncryptedMessageEventContent> {
        return returnEncryptMegolm ?: Result.failure(NotImplementedError())
    }

    val returnDecryptMegolm = mutableListOf<Result<DecryptedMegolmEvent<*>>>()

    override suspend fun decryptMegolm(
        encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>
    ): Result<DecryptedMegolmEvent<*>> {
        return if (returnDecryptMegolm.size > 1) returnDecryptMegolm.removeFirst() else returnDecryptMegolm.first()
    }
}
