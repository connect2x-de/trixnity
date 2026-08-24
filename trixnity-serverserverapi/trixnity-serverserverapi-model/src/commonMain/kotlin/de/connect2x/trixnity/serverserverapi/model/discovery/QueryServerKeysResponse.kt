package de.connect2x.trixnity.serverserverapi.model.discovery

import de.connect2x.trixnity.core.model.keys.Signed
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryServerKeysResponse(@SerialName("server_keys") val serverKeys: Set<Signed<ServerKeys, String>>)
