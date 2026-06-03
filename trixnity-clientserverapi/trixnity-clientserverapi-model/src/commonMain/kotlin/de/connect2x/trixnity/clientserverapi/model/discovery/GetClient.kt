package de.connect2x.trixnity.clientserverapi.model.discovery

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#getwell-knownmatrixclient">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/client")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetClient : MatrixEndpoint<Unit, DiscoveryInformation> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: DiscoveryInformation?
    ): KSerializer<DiscoveryInformation> = DiscoveryInformation.serializer()
}

@Deprecated("use GetClient", ReplaceWith("GetClient"))
typealias GetWellKnown = GetClient
