package de.connect2x.trixnity.serverserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.keys.Signatures
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.serverserverapi.model.policy.Sign

interface PolicyApiHandler {
    /**
     * @see [Sign]
     */
    suspend fun sign(context: MatrixEndpointContext<Sign, Signed<PersistentDataUnit<*>, String>, Signatures<String>>): Signatures<String>
}
