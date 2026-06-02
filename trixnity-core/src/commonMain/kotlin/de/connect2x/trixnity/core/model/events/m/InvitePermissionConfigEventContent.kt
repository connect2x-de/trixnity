package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InvitePermissionConfigEventContent(
    @SerialName("default_action")
    val defaultAction: DefaultAction? = null,
) : GlobalAccountDataEventContent {
    @Serializable
    enum class DefaultAction {
        @SerialName("block")
        BLOCK
    }
}
