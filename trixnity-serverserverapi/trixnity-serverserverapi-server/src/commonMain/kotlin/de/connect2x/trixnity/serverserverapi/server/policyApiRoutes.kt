package de.connect2x.trixnity.serverserverapi.server

import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

internal fun Route.policyApiRoutes(
    handler: PolicyApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::sign)
}
