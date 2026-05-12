package de.connect2x.trixnity.client.room

import de.connect2x.lognity.api.logger.Logger
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
import de.connect2x.trixnity.core.model.events.DecryptedMegolmEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private val log = Logger("de.connect2x.trixnity.client.room.MegolmRoomEventEncryptionService")

class MegolmRoomEventEncryptionService(
    private val roomStore: RoomStore,
    private val loadMembersService: LoadMembersService,
    private val roomStateStore: RoomStateStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyBackupService: KeyBackupService,
    private val outgoingRoomKeyRequestEventHandler: OutgoingRoomKeyRequestEventHandler,
    private val olmEncryptionService: OlmEncryptionService
) : RoomEventEncryptionService {
    override suspend fun encrypt(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent>? {
        if (roomStore.get(roomId).first()?.encrypted != true) return null
        val encryptionEventContent = withTimeoutOrNull(30.seconds) {
            roomStateStore.getByStateKey<EncryptionEventContent>(roomId).filterNotNull().first().content
        }
        if (encryptionEventContent?.algorithm != EncryptionAlgorithm.Megolm) return null
        if (content is ReactionEventContent) return Result.success(content)

        loadMembersService(roomId, wait = true)

        return olmEncryptionService.encryptMegolmCatching(content, roomId, encryptionEventContent)
    }

    override suspend fun decrypt(event: RoomEvent.MessageEvent<*>): Result<MessageEventContent>? {
        val content = event.content
        val roomId = event.roomId
        val eventId = event.id

        if (roomStore.get(roomId).first() == null) log.warn { "could not find $roomId in local data, waiting started" }
        if (!roomStore.get(roomId).filterNotNull().first().encrypted) return null
        val encryptionEventContent = withTimeoutOrNull(30.seconds) {
            roomStateStore.getByStateKey<EncryptionEventContent>(roomId).filterNotNull().first().content
        }
        if (encryptionEventContent?.algorithm != EncryptionAlgorithm.Megolm) return null
        if (content !is MegolmEncryptedMessageEventContent) return null

        val session = olmCryptoStore.getInboundMegolmSession(content.sessionId, roomId).first()
        val firstKnownIndex = session?.firstKnownIndex
        if (session == null) {
            log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
            waitForInboundMegolmSessionAndRequest(roomId, content.sessionId)
        }
        log.trace { "try to decrypt event $eventId in $roomId" }
        @Suppress("UNCHECKED_CAST")
        val encryptedEvent = event as RoomEvent<MegolmEncryptedMessageEventContent>

        val decryptEventAttempt = olmEncryptionService.decryptMegolmCatching(encryptedEvent)
        val exception = decryptEventAttempt.exceptionOrNull()
        val decryptedEvent =
            if (exception is RoomEventEncryptionServiceError && exception.cause is OlmEncryptionService.DecryptMegolmError.MegolmKeyUnknownMessageIndex) {
                log.debug { "unknwon message index, so we request key backup and start to wait for inbound megolm session to decrypt $eventId in $roomId again" }
                waitForInboundMegolmSessionAndRequest(
                    roomId,
                    content.sessionId,
                    firstKnownIndexLessThen = firstKnownIndex
                )
                olmEncryptionService.decryptMegolmCatching(encryptedEvent)
            } else decryptEventAttempt
        log.trace { "decrypted TimelineEvent $eventId in $roomId" }
        return decryptedEvent.map { it.content }
    }

    private suspend fun waitForInboundMegolmSessionAndRequest(
        roomId: RoomId,
        sessionId: String,
        firstKnownIndexLessThen: Long? = null
    ) = olmCryptoStore.waitForInboundMegolmSession(roomId, sessionId, firstKnownIndexLessThen) {
        keyBackupService.version.collectLatest { keyBackupVersion ->
            if (keyBackupVersion == null) outgoingRoomKeyRequestEventHandler.requestRoomKeys(roomId, sessionId)
            else keyBackupService.loadMegolmSession(roomId, sessionId)
        }
    }

    private suspend fun OlmEncryptionService.encryptMegolmCatching(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): Result<MegolmEncryptedMessageEventContent> =
        encryptMegolm(content, roomId, settings).recoverCatching { e ->
            if (e is OlmEncryptionService.EncryptMegolmError) throw RoomEventEncryptionServiceError(e)
            else throw e
        }

    private suspend fun OlmEncryptionService.decryptMegolmCatching(
        encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>
    ): Result<DecryptedMegolmEvent<*>> =
        decryptMegolm(encryptedEvent).recoverCatching { e ->
            if (e is OlmEncryptionService.DecryptMegolmError) throw RoomEventEncryptionServiceError(e)
            else throw e
        }
}

