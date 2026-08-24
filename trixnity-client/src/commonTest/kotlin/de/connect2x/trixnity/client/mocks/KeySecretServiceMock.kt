package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.key.KeySecretService
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import kotlinx.coroutines.flow.MutableStateFlow

class KeySecretServiceMock : KeySecretService {
    val decryptMissingSecretsCalled = MutableStateFlow<Triple<ByteArray, String, SecretKeyEventContent>?>(null)

    override suspend fun decryptOrCreateMissingSecrets(key: ByteArray, keyId: String, keyInfo: SecretKeyEventContent) {
        decryptMissingSecretsCalled.value = Triple(key, keyId, keyInfo)
    }
}
