package de.connect2x.trixnity.core.util

/**
 * Checks MXC URI grammar using the Matrix server-name grammar and media ID character whitelist. This does not fully
 * validate IP literals or check whether the server or media exists. The scheme is case-insensitive.
 *
 * @see [https://spec.matrix.org/v1.19/client-server-api/#matrix-content-mxc-uris]
 */
fun isValidMxcUri(uri: String): Boolean = MatrixIdRegex.mxcUri.matches(uri)
