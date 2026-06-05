package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

internal fun Route.discoveryApiRoutes(
    handler: DiscoveryApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::getClient)
    matrixEndpoint(json, contentMappings, handler::getSupport)
    matrixEndpoint(json, contentMappings, handler::getPolicyServer)
}
