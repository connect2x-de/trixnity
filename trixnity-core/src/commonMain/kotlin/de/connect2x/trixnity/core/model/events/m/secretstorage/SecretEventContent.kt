package de.connect2x.trixnity.core.model.events.m.secretstorage

import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.json.JsonElement

interface SecretEventContent : GlobalAccountDataEventContent {
    // Yeah this is messy, but is due to the spec, which does not allow type safe deserialization of these events.
    val encrypted: Map<String, JsonElement>
}
