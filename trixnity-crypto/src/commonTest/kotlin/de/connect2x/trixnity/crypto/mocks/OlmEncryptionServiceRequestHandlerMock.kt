package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.clientserverapi.model.key.ClaimKeys
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.crypto.olm.OlmEncryptionServiceRequestHandler

class OlmEncryptionServiceRequestHandlerMock : OlmEncryptionServiceRequestHandler {
    val claimKeysParams = mutableListOf<Map<UserId, Map<String, KeyAlgorithm>>>()
    var claimKeys: Result<ClaimKeys.Response>? = null
    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> {
        claimKeysParams.add(oneTimeKeys)
        return checkNotNull(claimKeys)
    }
}
