package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

interface RoomEventEncryptionService {
    /**
     * Encrypts given event. Returns null, when encryption algorithm is not supported by this service.
     *
     * Be aware, that this function can suspend a long time (for example, when the implementation needs to load all
     * members).
     */
    suspend fun encrypt(content: MessageEventContent, roomId: RoomId): Result<MessageEventContent>?

    /** Decrypts given event. Emits null, when encryption algorithm is not supported by this service. */
    suspend fun decrypt(event: RoomEvent.MessageEvent<*>): Flow<Result<MessageEventContent>?>
}

class RoomEventEncryptionServiceError(cause: Throwable) : IllegalStateException(cause)

/**
 * Encrypts given event. Returns null, when encryption algorithm is not supported by any [RoomEventEncryptionService].
 *
 * Be aware, that this function can suspend a possible infinite time (for example, when an implementation waits for
 * decryption keys).
 */
suspend fun List<RoomEventEncryptionService>.encrypt(
    content: MessageEventContent,
    roomId: RoomId,
): Result<MessageEventContent>? = firstNotNullOfOrNull { it.encrypt(content, roomId) }

/** Decrypts given event. Emits null, when encryption algorithm is not supported by any [RoomEventEncryptionService]. */
suspend fun List<RoomEventEncryptionService>.decrypt(
    event: RoomEvent.MessageEvent<*>
): Flow<Result<MessageEventContent>?> =
    if (isEmpty()) flowOf(null) else combine(map { it.decrypt(event) }) { it.filterNotNull().firstOrNull() }
