package de.connect2x.trixnity.serverserverapi.model.federation

import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.keys.Signed
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PduTransaction(
    @SerialName("origin")
    val origin: String,
    @SerialName("origin_server_ts")
    val originTimestamp: Long,
    @SerialName("pdus")
    val pdus: List<Signed<@Contextual PersistentDataUnit<*>, String>>
)
