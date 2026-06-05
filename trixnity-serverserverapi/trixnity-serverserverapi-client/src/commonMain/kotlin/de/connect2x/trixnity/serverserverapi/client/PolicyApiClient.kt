package de.connect2x.trixnity.serverserverapi.client

import de.connect2x.trixnity.api.client.MatrixApiClient
import de.connect2x.trixnity.core.model.keys.Signatures
import de.connect2x.trixnity.serverserverapi.model.SignedPersistentDataUnit
import de.connect2x.trixnity.serverserverapi.model.policy.Sign

interface PolicyApiClient {
    /**
     * @see [Sign]
     */
    suspend fun sign(pdu: SignedPersistentDataUnit<*>): Result<Signatures<String>>
}

class PolicyApiClientImpl(
    private val baseClient: MatrixApiClient
) : PolicyApiClient {
    override suspend fun sign(pdu: SignedPersistentDataUnit<*>): Result<Signatures<String>> =
        baseClient.request(Sign, pdu)
}
