package de.connect2x.trixnity.crypto.olm

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.DecryptedMegolmEvent
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.RoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.keys.CrossSigningKeys
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import de.connect2x.trixnity.core.model.keys.Key.Curve25519Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.model.keys.SessionKeyValue
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.driver.megolm.GroupSession
import de.connect2x.trixnity.crypto.driver.useAll
import de.connect2x.trixnity.crypto.invoke
import de.connect2x.trixnity.crypto.key.get
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService.DecryptMegolmError
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService.EncryptMegolmError
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import de.connect2x.trixnity.utils.AtomicUpdateAndRunResult
import de.connect2x.trixnity.utils.atomicUpdateAndRun
import de.connect2x.trixnity.utils.nextString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private val log = Logger("de.connect2x.trixnity.crypto.olm.MegolmEncryptionService")

interface MegolmEncryptionService {
    sealed interface EncryptMegolmError {
        data class CryptoDriverError(val error: CryptoDriverException) :
            EncryptMegolmError, IllegalStateException("error in crypto driver", error)

        data class NetworkError(val error: Throwable) :
            EncryptMegolmError, IllegalStateException("network error", error)
    }

    /**
     * Encrypt an event using megolm.
     *
     * Contains [EncryptMegolmError] on failure.
     */
    suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent,
    ): Result<MegolmEncryptedMessageEventContent>

    sealed interface DecryptMegolmError {
        data class CryptoDriverError(val error: CryptoDriverException) :
            DecryptMegolmError, IllegalStateException("error in crypto driver", error)

        class MegolmKeyNotFound : DecryptMegolmError, IllegalStateException("megolm key not found")

        class MegolmKeyUnknownMessageIndex :
            DecryptMegolmError, IllegalStateException("megolm key with unknown message index")

        data class ValidationFailed(val reason: String) :
            DecryptMegolmError, IllegalStateException("validation failed ($reason)")

        data class DeserializationError(val error: SerializationException) :
            DecryptMegolmError, IllegalStateException("deserialization failed", error)
    }

    /**
     * Decrypt an event using megolm.
     *
     * Contains [DecryptMegolmError] on failure.
     */
    suspend fun decryptMegolm(
        encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>
    ): Result<DecryptedMegolmEvent<*>>
}

class MegolmEncryptionServiceImpl(
    userInfo: UserInfo,
    private val json: Json,
    private val store: OlmStore,
    private val requests: MegolmEncryptionServiceRequestHandler,
    private val olmEncryptionService: OlmEncryptionService,
    private val clock: Clock,
    private val driver: CryptoDriver,
) : MegolmEncryptionService {

    private val ownUserId: UserId = userInfo.userId
    private val ownDeviceId: String = userInfo.deviceId
    private val ownEd25519Key: Ed25519Key = userInfo.signingPublicKey
    private val ownCurve25519Key: Curve25519Key = userInfo.identityPublicKey

    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent,
    ): Result<MegolmEncryptedMessageEventContent> =
        runCatchingCancellationAware {
                val rotationPeriodMs = settings.rotationPeriodMs
                val rotationPeriodMsgs = settings.rotationPeriodMsgs
                val pickleKey =
                    try {
                        driver.key.pickleKey(store.getOlmPickleKey())
                    } catch (exception: CryptoDriverException) {
                        throw EncryptMegolmError.CryptoDriverError(exception)
                    }
                atomicUpdateAndRun(
                    getValue = { store.getOutboundMegolmSession(roomId) },
                    updateValue = { store.updateOutboundMegolmSession(roomId, it) },
                ) { storedMegolmSession ->
                    if (
                        storedMegolmSession != null &&
                            (rotationPeriodMs == null ||
                                ((storedMegolmSession.createdAt + rotationPeriodMs.milliseconds) > clock.now())) &&
                            (rotationPeriodMsgs == null ||
                                (storedMegolmSession.encryptedMessageCount < rotationPeriodMsgs))
                    ) {
                        log.debug { "encrypt megolm event with existing session" }
                        val (encryptionResult, pickledSession) =
                            try {
                                driver.megolm.groupSession.fromPickle(storedMegolmSession.pickled, pickleKey).use {
                                    outboundSession ->
                                    outboundSession.encrypt(
                                        content = content,
                                        roomId = roomId,
                                        newDevices =
                                            storedMegolmSession.newDevices
                                                .flatMap { (userId, deviceIds) -> deviceIds.map { userId to it } }
                                                .toSet(),
                                    ) to outboundSession.pickle(pickleKey)
                                }
                            } catch (olmLibraryException: CryptoDriverException) {
                                throw EncryptMegolmError.CryptoDriverError(olmLibraryException)
                            }
                        return@atomicUpdateAndRun AtomicUpdateAndRunResult(
                            result = encryptionResult,
                            update =
                                storedMegolmSession.copy(
                                    encryptedMessageCount = storedMegolmSession.encryptedMessageCount + 1,
                                    pickled = pickledSession,
                                    newDevices = emptyMap(),
                                ),
                        )
                    }

                    log.debug { "encrypt megolm event with new session" }
                    val newUserDevices =
                        store.getDevices(roomId, store.getHistoryVisibility(roomId).membershipsAllowedToReceiveKey)
                    val (encryptionResult, pickledSession, storedInboundMegolmSession) =
                        try {
                            useAll(
                                { driver.megolm.groupSession() },
                                { it.sessionKey.use(driver.megolm.inboundGroupSession::invoke) },
                            ) { outboundSession, inboundSession ->
                                Triple(
                                    outboundSession.encrypt(
                                        content = content,
                                        roomId = roomId,
                                        newDevices = newUserDevices,
                                    ),
                                    outboundSession.pickle(pickleKey),
                                    StoredInboundMegolmSession(
                                        senderKey = ownCurve25519Key.value,
                                        sessionId = inboundSession.sessionId,
                                        roomId = roomId,
                                        firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
                                        hasBeenBackedUp = false,
                                        isTrusted = true,
                                        senderSigningKey = ownEd25519Key.value,
                                        forwardingCurve25519KeyChain = listOf(),
                                        pickled = inboundSession.pickle(pickleKey),
                                    ),
                                )
                            }
                        } catch (olmLibraryException: CryptoDriverException) {
                            throw EncryptMegolmError.CryptoDriverError(olmLibraryException)
                        }
                    store.updateInboundMegolmSession(storedInboundMegolmSession.sessionId, roomId) {
                        storedInboundMegolmSession
                    }
                    return@atomicUpdateAndRun AtomicUpdateAndRunResult(
                        result = encryptionResult,
                        update =
                            StoredOutboundMegolmSession(
                                roomId = roomId,
                                createdAt = clock.now(),
                                encryptedMessageCount = 1,
                                newDevices = emptyMap(),
                                pickled = pickledSession,
                            ),
                    )
                }
            }
            .onFailure { log.warn(it) { "encrypt megolm failed" } }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun GroupSession.encrypt(
        content: MessageEventContent,
        roomId: RoomId,
        newDevices: Set<Pair<UserId, String>>,
    ): MegolmEncryptedMessageEventContent {
        val newDevicesWithoutUs = newDevices - (ownUserId to ownDeviceId)

        if (newDevicesWithoutUs.isNotEmpty()) {
            val roomKeyEventContent =
                RoomKeyEventContent(
                    roomId = roomId,
                    sessionId = sessionId,
                    sessionKey = SessionKeyValue.of(sessionKey),
                    algorithm = Megolm,
                )

            val eventsToSend =
                olmEncryptionService
                    .encryptOlm(roomKeyEventContent, newDevicesWithoutUs)
                    .mapNotNull { (recipient, encryptOlmResult) ->
                        encryptOlmResult
                            .onFailure {
                                val e = it as? EncryptOlmError
                                when (e) {
                                    is EncryptOlmError.CryptoDriverError ->
                                        throw EncryptMegolmError.CryptoDriverError(e.error)

                                    is EncryptOlmError.NetworkError -> throw EncryptMegolmError.NetworkError(e.error)

                                    is EncryptOlmError.DehydratedDeviceNotCrossSigned -> {
                                        log.info {
                                            "will not send megolm session to $recipient, because dehydrated device not cross signed"
                                        }
                                    }

                                    is EncryptOlmError.NoOlmSupported -> {
                                        log.info {
                                            "will not send megolm session to $recipient, because olm not supported"
                                        }
                                    }

                                    is EncryptOlmError.RemoteHomeserverNotReachable -> {
                                        // TODO happens rarely, but we need a recovery mechanism (e.g. request room
                                        // keys)!
                                        log.warn {
                                            "will not send megolm session to $recipient, because remote homeserver not reachable and therefore new olm session could not be created"
                                        }
                                    }

                                    null -> {
                                        log.error(it) { "unexpected error" }
                                    }
                                }
                            }
                            .getOrNull()
                            ?.let { recipient to it }
                    }
                    .groupBy { it.first.first }
                    .mapValues { it.value.associate { it.first.second to it.second } }
            if (eventsToSend.isNotEmpty()) {
                log.debug { "send megolm key to devices: ${eventsToSend.mapValues { it.value.keys }}" }
                requests
                    .sendToDevice(eventsToSend, SecureRandom.nextString(22))
                    .onFailure { throw EncryptMegolmError.NetworkError(it) }
                    .getOrThrow()
            }
        }

        val serializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
        val event = DecryptedMegolmEvent(content, roomId)
        checkNotNull(serializer)

        val encryptedContent = encrypt(json.encodeToString(serializer, event))

        return MegolmEncryptedMessageEventContent(
            ciphertext = MegolmMessageValue.of(encryptedContent),
            senderKey = ownCurve25519Key.value,
            deviceId = ownDeviceId,
            sessionId = sessionId,
            relatesTo = relatesToForEncryptedEvent(content),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun decryptMegolm(
        encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>
    ): Result<DecryptedMegolmEvent<*>> =
        runCatchingCancellationAware {
                val roomId = encryptedEvent.roomId
                val encryptedContent = encryptedEvent.content
                val sessionId = encryptedContent.sessionId

                val storedSession =
                    store.getInboundMegolmSession(sessionId, roomId) ?: throw DecryptMegolmError.MegolmKeyNotFound()

                val pickleKey =
                    try {
                        driver.key.pickleKey(store.getOlmPickleKey())
                    } catch (exception: CryptoDriverException) {
                        throw DecryptMegolmError.CryptoDriverError(exception)
                    }

                val (plaintext, messageIndex) =
                    try {
                        driver.megolm.inboundGroupSession
                            .fromPickle(pickle = storedSession.pickled, pickleKey = pickleKey)
                            .use { session -> session.decrypt(driver.megolm.message(encryptedContent.ciphertext)) }
                    } catch (e: CryptoDriverException) {
                        when {
                            e.message == "UNKNOWN_MESSAGE_INDEX" ->
                                throw DecryptMegolmError.MegolmKeyUnknownMessageIndex()
                            e.message?.contains("unknown message index") == true ->
                                throw DecryptMegolmError.MegolmKeyUnknownMessageIndex()
                            else -> throw DecryptMegolmError.CryptoDriverError(e)
                        }
                    }

                val serializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
                checkNotNull(serializer)
                val decryptedEvent =
                    try {
                        json.decodeFromJsonElement(
                            serializer,
                            addRelatesToToDecryptedEvent(plaintext, encryptedContent.relatesTo),
                        )
                    } catch (e: SerializationException) {
                        throw DecryptMegolmError.DeserializationError(e)
                    }
                store.updateInboundMegolmMessageIndex(sessionId, roomId, messageIndex.toLong()) { storedIndex ->
                    if (encryptedEvent.roomId != decryptedEvent.roomId)
                        throw DecryptMegolmError.ValidationFailed("roomId did not match")
                    if (
                        storedIndex != null &&
                            (storedIndex.eventId != encryptedEvent.id ||
                                storedIndex.originTimestamp != encryptedEvent.originTimestamp)
                    )
                        throw DecryptMegolmError.ValidationFailed("message index did not match")

                    storedIndex
                        ?: StoredInboundMegolmMessageIndex(
                            sessionId,
                            roomId,
                            messageIndex.toLong(),
                            encryptedEvent.id,
                            encryptedEvent.originTimestamp,
                        )
                }

                decryptedEvent
            }
            .onFailure { log.warn(it) { "decrypt megolm failed" } }

    private fun addRelatesToToDecryptedEvent(decryptionJson: String, relatesTo: RelatesTo?) =
        JsonObject(
            buildMap {
                val originalJsonObject = json.decodeFromString<JsonObject>(decryptionJson).jsonObject
                putAll(originalJsonObject)
                relatesTo?.let { relatesTo ->
                    originalJsonObject["content"]?.jsonObject?.let { content ->
                        put(
                            "content",
                            JsonObject(
                                buildMap {
                                    putAll(content)
                                    put(
                                        "m.relates_to",
                                        JsonObject(
                                            buildMap {
                                                content["m.relates_to"]?.jsonObject?.let { putAll(it) }
                                                putAll(json.encodeToJsonElement(relatesTo).jsonObject)
                                            }
                                        ),
                                    )
                                }
                            ),
                        )
                    }
                }
            }
        )

    private fun relatesToForEncryptedEvent(content: EventContent) =
        if (content is MessageEventContent) {
            val relatesTo = content.relatesTo
            if (relatesTo is RelatesTo.Replace) relatesTo.copy(newContent = null) else relatesTo
        } else null

    private inline fun <T, R> T.runCatchingCancellationAware(block: T.() -> R): Result<R> {
        return try {
            Result.success(block())
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private inline fun <reified T> Signed<T, UserId>.getSelfSigningKey(): Ed25519Key? {
        return when (val signed = this.signed) {
            is DeviceKeys -> signed.keys.get()
            is CrossSigningKeys -> signed.keys.get()
            else -> null
        }
    }
}
