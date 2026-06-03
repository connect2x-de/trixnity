package de.connect2x.trixnity.serverserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.core.model.keys.Signatures
import de.connect2x.trixnity.serverserverapi.model.SignedPersistentDataUnit
import de.connect2x.trixnity.serverserverapi.model.policy.Sign

interface PolicyApiHandler {
    /**
     * @see [Sign]
     */
    suspend fun sign(context: MatrixEndpointContext<Sign, SignedPersistentDataUnit<*>, Signatures<String>>): Signatures<String>
}
