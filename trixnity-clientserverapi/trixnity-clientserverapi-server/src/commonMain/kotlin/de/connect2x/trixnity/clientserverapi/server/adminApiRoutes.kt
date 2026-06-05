package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

internal fun Route.adminApiRoutes(
    handler: AdminApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    matrixEndpoint(json, contentMappings, handler::getSuspend)
    matrixEndpoint(json, contentMappings, handler::setSuspend)
    matrixEndpoint(json, contentMappings, handler::getLock)
    matrixEndpoint(json, contentMappings, handler::setLock)
    matrixEndpoint(json, contentMappings, handler::whoIs)
}
