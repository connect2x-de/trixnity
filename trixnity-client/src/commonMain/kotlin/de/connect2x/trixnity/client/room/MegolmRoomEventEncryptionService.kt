package de.connect2x.trixnity.client.room

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.debug
import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.client.key.OutgoingRoomKeyRequestEventHandler
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.getByStateKey
import de.connect2x.trixnity.client.store.waitForInboundMegolmSession
import de.connect2x.trixnity.client.user.LoadMembersService
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.PlaintextMegolmEvent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.utils.retryFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val log = Logger("de.connect2x.trixnity.client.room.MegolmRoomEventEncryptionService")

class MegolmRoomEventEncryptionService(
    private val roomStore: RoomStore,
    private val loadMembersService: LoadMembersService,
    private val roomStateStore: RoomStateStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyBackupService: KeyBackupService,
    private val outgoingRoomKeyRequestEventHandler: OutgoingRoomKeyRequestEventHandler,
    private val megolmEncryptionService: MegolmEncryptionService,
) : RoomEventEncryptionService {
    override suspend fun encrypt(content: MessageEventContent, roomId: RoomId): Result<MessageEventContent>? {
        if (roomStore.get(roomId).first()?.encrypted != true) return null
        val encryptionEventContent =
            withTimeoutOrNull(30.seconds) {
                roomStateStore.getByStateKey<EncryptionEventContent>(roomId).filterNotNull().first().content
            }
        if (encryptionEventContent?.algorithm != EncryptionAlgorithm.Megolm) return null
        if (content is ReactionEventContent) return Result.success(content)
        if (content is RedactionEventContent) return Result.success(content)

        loadMembersService(roomId, wait = true)

        return megolmEncryptionService.encryptMegolmCatching(content, roomId, encryptionEventContent)
    }

    override suspend fun decrypt(event: RoomEvent.MessageEvent<*>): Flow<Result<MessageEventContent>?> {
        val content = event.content
        val roomId = event.roomId
        val eventId = event.id

        return retryFlow(
            scheduleBase = Duration.ZERO,
            onError = { error, _ -> log.debug(error) { "could not decrypt event, retry again" } },
        ) {
            if (roomStore.get(roomId).first() == null)
                log.warn { "could not find $roomId in local data, waiting started" }
            if (!roomStore.get(roomId).filterNotNull().first().encrypted) {
                emit(null)
                return@retryFlow
            }
            val encryptionEventContent =
                withTimeoutOrNull(30.seconds) {
                    roomStateStore.getByStateKey<EncryptionEventContent>(roomId).filterNotNull().first().content
                }
            if (encryptionEventContent?.algorithm != EncryptionAlgorithm.Megolm) {
                emit(null)
                return@retryFlow
            }
            if (content !is MegolmEncryptedMessageEventContent) {
                emit(null)
                return@retryFlow
            }

            val session = olmCryptoStore.getInboundMegolmSession(content.sessionId, roomId).first()

            log.trace { "try to decrypt event $eventId in $roomId" }
            @Suppress("UNCHECKED_CAST") val encryptedEvent = event as RoomEvent<MegolmEncryptedMessageEventContent>

            val decryptedEventResult = megolmEncryptionService.decryptMegolmCatching(encryptedEvent)
            emit(decryptedEventResult.map { it.content })
            val exception = decryptedEventResult.exceptionOrNull() as? RoomEventEncryptionServiceError

            if (exception?.cause is MegolmEncryptionService.DecryptMegolmError) {
                log.debug {
                    "${exception.cause}, so we request key backup and start to wait for inbound megolm session to decrypt $eventId in $roomId again"
                }
                waitForInboundMegolmSessionAndRequest(
                    roomId = roomId,
                    sessionId = content.sessionId,
                    firstKnownIndexLessThan = (session as? StoredInboundMegolmSession.Key)?.firstKnownIndex,
                    allowWithheld = exception.cause is MegolmEncryptionService.DecryptMegolmError.MegolmKeyNotFound,
                    othenThan = session,
                )
                throw exception
            }
        }
    }

    private suspend fun waitForInboundMegolmSessionAndRequest(
        roomId: RoomId,
        sessionId: String,
        firstKnownIndexLessThan: Long? = null,
        allowWithheld: Boolean = false,
        othenThan: StoredInboundMegolmSession? = null,
    ) =
        olmCryptoStore.waitForInboundMegolmSession(
            roomId = roomId,
            sessionId = sessionId,
            firstKnownIndexLessThan = firstKnownIndexLessThan,
            allowWithheld = allowWithheld,
            othenThan = othenThan,
        ) {
            keyBackupService.version.collectLatest { keyBackupVersion ->
                if (keyBackupVersion == null) outgoingRoomKeyRequestEventHandler.requestRoomKeys(roomId, sessionId)
                else keyBackupService.loadMegolmSession(roomId, sessionId)
            }
        }

    private suspend fun MegolmEncryptionService.encryptMegolmCatching(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent,
    ): Result<MegolmEncryptedMessageEventContent> =
        encryptMegolm(content, roomId, settings).recoverCatching { e ->
            if (e is MegolmEncryptionService.EncryptMegolmError) throw RoomEventEncryptionServiceError(e) else throw e
        }

    private suspend fun MegolmEncryptionService.decryptMegolmCatching(
        encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>
    ): Result<PlaintextMegolmEvent<*>> =
        decryptMegolm(encryptedEvent).recoverCatching { e ->
            if (e is MegolmEncryptionService.DecryptMegolmError) throw RoomEventEncryptionServiceError(e) else throw e
        }
}
