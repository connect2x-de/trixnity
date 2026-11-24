package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService

class OlmEncryptionServiceMock : OlmEncryptionService {
    data class EncryptOlmCall(
        val content: EventContent,
        val recipients: Set<Pair<UserId, String>>,
        val excludeUntrustedDevices: Boolean?,
    )

    val encryptOlm = mutableMapOf<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>>()
    val encryptOlmWithoutExcludingUntrusted =
        mutableMapOf<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>>()
    val encryptOlmCalls = mutableListOf<EncryptOlmCall>()

    override suspend fun encryptOlm(
        content: EventContent,
        recipientUserId: UserId,
        recipientDeviceId: String,
        excludeUntrustedDevices: Boolean?,
    ): Result<OlmEncryptedToDeviceEventContent> {
        encryptOlmCalls.add(
            EncryptOlmCall(content, setOf(recipientUserId to recipientDeviceId), excludeUntrustedDevices)
        )
        val map = if (excludeUntrustedDevices == false) encryptOlmWithoutExcludingUntrusted else encryptOlm
        return checkNotNull(map[recipientUserId to recipientDeviceId])
    }

    var encryptOlmRecipients: Set<Pair<UserId, String>> = emptySet()

    override suspend fun encryptOlm(
        content: EventContent,
        recipients: Set<Pair<UserId, String>>,
        excludeUntrustedDevices: Boolean?,
    ): Map<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>> {
        encryptOlmRecipients = recipients
        encryptOlmCalls.add(EncryptOlmCall(content, recipients, excludeUntrustedDevices))
        val map = if (excludeUntrustedDevices == false) encryptOlmWithoutExcludingUntrusted else encryptOlm
        return recipients.mapNotNull { recipient -> map[recipient]?.let { recipient to it } }.toMap()
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
