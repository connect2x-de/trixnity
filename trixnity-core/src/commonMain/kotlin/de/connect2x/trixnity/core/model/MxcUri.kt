package de.connect2x.trixnity.core.model

import de.connect2x.trixnity.core.util.MatrixIdRegex
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MxcUri(val full: String) {

    constructor(serverName: String, mediaId: String) : this("mxc://$serverName/$mediaId")

    companion object {
        /**
         * Checks MXC URI grammar using the Matrix server-name grammar and media ID character whitelist. This does not
         * fully validate IP literals or check whether the server or media exists. The scheme is case-insensitive.
         *
         * @see [https://spec.matrix.org/v1.19/client-server-api/#matrix-content-mxc-uris]
         */
        fun isValid(uri: String): Boolean = uri.matches(MatrixIdRegex.mxcUri)
    }

    val serverName: String
        get() = full.substringAfter("://").substringBefore('/')

    val mediaId: String
        get() = full.substringAfter("://").substringAfter('/')

    val isValid: Boolean
        get() = isValid(full)

    override fun toString() = full
}
