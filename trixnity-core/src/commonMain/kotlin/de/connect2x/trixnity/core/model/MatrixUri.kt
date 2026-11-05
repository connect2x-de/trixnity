package de.connect2x.trixnity.core.model

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.core.util.Reference
import io.ktor.http.Parameters
import io.ktor.http.URLDecodeException
import io.ktor.http.URLParserException
import io.ktor.http.Url
import io.ktor.http.encodeURLPathPart
import io.ktor.http.formUrlEncodeTo
import io.ktor.http.parametersOf
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

private val log = Logger {}

/**
 * A `matrix:` URI referring to a user, room alias, room or event. Construction preserves the supplied string without
 * validation. Use [isValid] to validate it or [toReference] to parse it.
 *
 * @see [https://spec.matrix.org/v1.19/appendices/#matrix-uri-scheme]
 */
@Serializable
@JvmInline
value class MatrixUri(val full: String) {

    companion object {
        fun from(userId: UserId, parameters: Parameters = parametersOf()): MatrixUri =
            create("u/${encodeId(userId.full, UserId.sigilCharacter)}", parameters)

        fun from(roomId: RoomId, parameters: Parameters = parametersOf()): MatrixUri =
            create("roomid/${encodeId(roomId.full, RoomId.sigilCharacter)}", parameters)

        fun from(roomAliasId: RoomAliasId, parameters: Parameters = parametersOf()): MatrixUri =
            create("r/${encodeId(roomAliasId.full, RoomAliasId.sigilCharacter)}", parameters)

        fun from(roomId: RoomId, eventId: EventId, parameters: Parameters = parametersOf()): MatrixUri =
            create(
                "roomid/${encodeId(roomId.full, RoomId.sigilCharacter)}" +
                    "/e/${encodeId(eventId.full, EventId.sigilCharacter)}",
                parameters,
            )

        fun isValid(uri: String): Boolean =
            when (val reference = MatrixUri(uri).toReference()) {
                is Reference.User -> reference.userId.isValid
                is Reference.Room -> reference.roomId.isValid
                is Reference.RoomAlias -> reference.roomAliasId.isValid
                is Reference.Event -> reference.roomId?.isValid == true && reference.eventId.isValid
                is Reference.Link,
                null -> false
            }
    }

    val isValid: Boolean
        get() = isValid(full)

    override fun toString() = full
}

/** Parses a Matrix URI without validating its identifiers. Authority and fragment are ignored. */
fun MatrixUri.toReference(): Reference? {
    val url =
        try {
            Url(full)
        } catch (exception: URLParserException) {
            log.trace { "malformed matrix URI: invalid URL (${exception.message})" }
            return null
        } catch (exception: URLDecodeException) {
            log.trace { "malformed matrix URI: invalid URL encoding (${exception.message})" }
            return null
        }
    val path = url.segments
    val supportedPath =
        when (path.size) {
            2 -> path[0] in listOf("u", "r", "roomid")
            4 -> path[0] == "roomid" && path[2] == "e"
            else -> false
        }
    return if (url.protocol.name == "matrix" && supportedPath) Reference.parseLink(full) else null
}

/**
 * Returns null for ordinary links. Roomless events retain the legacy `matrix:e/...` representation, which
 * [MatrixUri.isValid] rejects because event URIs require a room.
 */
fun Reference.toMatrixUri(): MatrixUri? =
    when (this) {
        is Reference.User -> MatrixUri.from(userId, parameters)
        is Reference.Room -> MatrixUri.from(roomId, parameters)
        is Reference.RoomAlias -> MatrixUri.from(roomAliasId, parameters)
        is Reference.Event -> {
            val roomId = this.roomId
            if (roomId != null) MatrixUri.from(roomId, eventId, parameters)
            else create("e/${encodeId(eventId.full, EventId.sigilCharacter)}", parameters)
        }
        is Reference.Link -> null
    }

private fun encodeId(id: String, sigil: Char): String = id.removePrefix(sigil.toString()).encodeURLPathPart()

private fun create(path: String, parameters: Parameters): MatrixUri =
    MatrixUri(
        buildString {
            append("matrix:")
            append(path)
            if (!parameters.isEmpty()) {
                append('?')
                parameters.formUrlEncodeTo(this)
            }
        }
    )
