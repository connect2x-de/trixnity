package de.connect2x.trixnity.serverserverapi.model.federation

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.Profile
import de.connect2x.trixnity.core.model.ProfileField
import de.connect2x.trixnity.core.model.UserId
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/latest/server-server-api/#get_matrixfederationv1queryprofile">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/query/profile")
@HttpMethod(GET)
data class QueryProfile(
    @SerialName("user_id") val userId: UserId,
    @SerialName("field") val field: ProfileField.Key<*>? = null,
) : MatrixEndpoint<Unit, Profile>
