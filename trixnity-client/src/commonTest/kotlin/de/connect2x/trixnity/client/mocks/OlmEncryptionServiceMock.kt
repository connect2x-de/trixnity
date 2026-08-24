package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService

class OlmEncryptionServiceMock : OlmEncryptionService {

    var returnEncryptOlm: Result<OlmEncryptedToDeviceEventContent>? = null
    var encryptOlmCalled: Triple<EventContent, UserId, String>? = null

    override suspend fun encryptOlm(
        content: EventContent,
        recipientUserId: UserId,
        recipientDeviceId: String,
    ): Result<OlmEncryptedToDeviceEventContent> {
        encryptOlmCalled = Triple(content, recipientUserId, recipientDeviceId)
        return returnEncryptOlm ?: Result.failure(NotImplementedError())
    }

    override suspend fun encryptOlm(
        content: EventContent,
        recipients: Set<Pair<UserId, String>>,
    ): Map<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>> {
        encryptOlmCalled = Triple(content, recipients.first().first, recipients.first().second)
        return recipients.associateWith { returnEncryptOlm ?: Result.failure(NotImplementedError()) }
    }

    override suspend fun recoverOlm(
        olmRecovery: OlmEncryptionService.OlmRecovery
    ): Result<OlmEncryptedToDeviceEventContent?> {
        throw NotImplementedError()
    }

    lateinit var returnDecryptOlm: PlaintextOlmEvent<*>

    override suspend fun decryptOlm(
        event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>
    ): Result<PlaintextOlmEvent<*>> {
        return Result.success(returnDecryptOlm)
    }
}
