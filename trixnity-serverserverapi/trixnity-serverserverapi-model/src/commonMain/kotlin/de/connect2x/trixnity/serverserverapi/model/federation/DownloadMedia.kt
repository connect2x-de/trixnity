package de.connect2x.trixnity.serverserverapi.model.federation

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * @see <a href="https://spec.matrix.org/v1.11/server-server-api/#get_matrixfederationv1mediadownloadmediaid">matrix
 *   spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/media/download/{mediaId}")
@HttpMethod(GET)
data class DownloadMedia(
    @SerialName("mediaId") val mediaId: String,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
) : MatrixEndpoint<Unit, Media> {

    @Transient override val requestContentType = ContentType.Application.Json

    @Transient override val responseContentType = ContentType.MultiPart.Mixed
}
