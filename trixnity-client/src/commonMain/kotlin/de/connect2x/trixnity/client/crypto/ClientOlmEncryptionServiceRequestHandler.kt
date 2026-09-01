package de.connect2x.trixnity.client.crypto

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.model.key.ClaimKeys
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.crypto.olm.OlmEncryptionServiceRequestHandler

class ClientOlmEncryptionServiceRequestHandler(private val api: MatrixClientServerApiClient) :
    OlmEncryptionServiceRequestHandler {
    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> =
        api.key.claimKeys(oneTimeKeys)
}
