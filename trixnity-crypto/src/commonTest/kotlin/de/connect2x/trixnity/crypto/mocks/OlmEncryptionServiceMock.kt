package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService

class OlmEncryptionServiceMock : OlmEncryptionService {
    val encryptOlm = mutableMapOf<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>>()

    override suspend fun encryptOlm(
        content: EventContent,
        recipientUserId: UserId,
        recipientDeviceId: String,
    ): Result<OlmEncryptedToDeviceEventContent> = checkNotNull(encryptOlm[recipientUserId to recipientDeviceId])

    var encryptOlmRecipients: Set<Pair<UserId, String>> = emptySet()

    override suspend fun encryptOlm(
        content: EventContent,
        recipients: Set<Pair<UserId, String>>,
    ): Map<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>> {
        encryptOlmRecipients = recipients
        return encryptOlm
    }

    var recoverOlm: Result<OlmEncryptedToDeviceEventContent?>? = null

    override suspend fun recoverOlm(
        olmRecovery: OlmEncryptionService.OlmRecovery
    ): Result<OlmEncryptedToDeviceEventContent?> = checkNotNull(recoverOlm)

    var decryptOlm = mutableListOf<Result<PlaintextOlmEvent<*>>>()

    override suspend fun decryptOlm(
        event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>
    ): Result<PlaintextOlmEvent<*>> = decryptOlm.removeFirst()
}
