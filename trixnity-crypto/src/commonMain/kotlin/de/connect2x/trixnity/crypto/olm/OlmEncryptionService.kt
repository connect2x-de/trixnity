package de.connect2x.trixnity.crypto.olm

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.clientserverapi.model.key.ClaimKeys
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent
import de.connect2x.trixnity.core.model.events.m.DummyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType
import de.connect2x.trixnity.core.model.keys.CrossSigningKeys
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.Key.Curve25519Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.Key.SignedCurve25519Key
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.driver.olm.Session
import de.connect2x.trixnity.crypto.driver.useAll
import de.connect2x.trixnity.crypto.invoke
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.key.get
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.DecryptOlmError
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError.RemoteHomeserverNotReachable
import de.connect2x.trixnity.crypto.sign.SignService
import de.connect2x.trixnity.crypto.sign.VerifyResult
import de.connect2x.trixnity.crypto.sign.verify
import de.connect2x.trixnity.utils.AtomicUpdateAndRunResult
import de.connect2x.trixnity.utils.KeyedMutex
import de.connect2x.trixnity.utils.atomicUpdateAndRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val log = Logger("de.connect2x.trixnity.crypto.olm.OlmEncryptionService")

interface OlmEncryptionService {

    sealed interface EncryptOlmError {
        data class CryptoDriverError(
            val error: CryptoDriverException,
        ) : EncryptOlmError, IllegalStateException("error in crypto driver", error)

        class NoOlmSupported(
            val reason: String
        ) : EncryptOlmError, IllegalStateException(reason)

        data class NetworkError(
            val error: Throwable,
        ) : EncryptOlmError, IllegalStateException("network error", error)

        class RemoteHomeserverNotReachable(
            val reason: String,
        ) : EncryptOlmError,
            IllegalStateException("remote server error while claiming keys")

        class DehydratedDeviceNotCrossSigned : EncryptOlmError,
            IllegalStateException("when encrypting to dehydrated device, the device must be cross signed")
    }

    /**
     * Encrypt an event using olm.
     *
     * Result contains [EncryptOlmError] on failure.
     */
    suspend fun encryptOlm(
        content: EventContent,
        recipientUserId: UserId,
        recipientDeviceId: String,
    ): Result<OlmEncryptedToDeviceEventContent>

    /**
     * Encrypt an event using olm. Calling this when sending the same event for multiple recipients allows some internal optimizations.
     *
     * Result contains [EncryptOlmError] on failure.
     */
    suspend fun encryptOlm(
        content: EventContent,
        recipients: Set<Pair<UserId, String>>,
    ): Map<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>>

    data class OlmRecovery(
        val userId: UserId,
        val deviceId: String,
        val lastTry: Instant,
    )

    /**
     * Encrypt a dummy event using [encryptOlm]. Result contains `null` when the session is already recovered.
     */
    suspend fun recoverOlm(
        olmRecovery: OlmRecovery,
    ): Result<OlmEncryptedToDeviceEventContent?>

    sealed interface DecryptOlmError {
        val olmRecovery: OlmRecovery?

        data class CryptoDriverError(
            override val olmRecovery: OlmRecovery?,
            val error: CryptoDriverException,
        ) : DecryptOlmError, IllegalStateException("error in crypto driver", error)

        class NoOlmSupported(
            val reason: String
        ) : DecryptOlmError, IllegalStateException(reason) {
            override val olmRecovery = null
        }

        class SenderDidNotEncryptForThisDevice : DecryptOlmError,
            IllegalStateException("no ciphertext found for this device") {
            override val olmRecovery = null
        }

        data class NoMatchingOlmSessionFound(
            override val olmRecovery: OlmRecovery?,
        ) : DecryptOlmError,
            IllegalStateException("no matching olm session found")

        class TooManySessions : DecryptOlmError,
            IllegalStateException("too many olm sessions created") {
            override val olmRecovery = null
        }

        data class ValidationFailed(
            val reason: String,
        ) : DecryptOlmError, IllegalStateException("validation failed ($reason)") {
            override val olmRecovery = null
        }

        data class DeserializationError(
            val error: SerializationException,
        ) : DecryptOlmError, IllegalStateException("deserialization failed", error) {
            override val olmRecovery = null
        }

        class DehydratedDeviceNotAllowed : DecryptOlmError,
            IllegalStateException("decrypting from a dehydrated device is not allowed") {
            override val olmRecovery = null
        }
    }

    /**
     * Decrypt an event using olm.
     *
     * Avoid calling this out of order for the same [OlmEncryptedToDeviceEventContent.senderKey] to prevent decryption errors.
     *
     * Be aware to call [recoverOlm] and send the event when [DecryptOlmError.olmRecovery] is set.
     *
     * Result contains [DecryptOlmError] on failure.
     */
    suspend fun decryptOlm(
        event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>,
    ): Result<PlaintextOlmEvent<*>>
}

class OlmEncryptionServiceImpl(
    userInfo: UserInfo,
    private val json: Json,
    private val store: OlmStore,
    private val requests: OlmEncryptionServiceRequestHandler,
    private val signService: SignService,
    private val clock: Clock,
    private val driver: CryptoDriver,
) : OlmEncryptionService {
    private val ownUserId: UserId = userInfo.userId
    private val ownDeviceId: String = userInfo.deviceId
    private val ownSigningKey: Ed25519Key = userInfo.signingPublicKey
    private val ownIdentityKey: Curve25519Key = userInfo.identityPublicKey

    override suspend fun encryptOlm(
        content: EventContent,
        recipientUserId: UserId,
        recipientDeviceId: String,
    ): Result<OlmEncryptedToDeviceEventContent> =
        encryptOlm(
            content = content,
            recipientUserId = recipientUserId,
            recipientDeviceId = recipientDeviceId,
            forceNewSessionWhenNotLastUsedAfter = null,
            claimOneTimeKey = {
                requests.claimKeys(mapOf(recipientUserId to mapOf(recipientDeviceId to KeyAlgorithm.SignedCurve25519)))
                    .getOneTimeKeyOrThrow(recipientUserId, recipientDeviceId)
            }
        ).map { requireNotNull(it) }

    override suspend fun encryptOlm(
        content: EventContent,
        recipients: Set<Pair<UserId, String>>
    ): Map<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>> = coroutineScope {
        val finished = MutableStateFlow(setOf<Pair<UserId, String>>())
        val claimRequests = MutableStateFlow(setOf<Pair<UserId, String>>())
        val claimOneTimeKeysResponse = async {
            combine(
                finished,
                claimRequests,
            ) { finished, claimRequests ->
                finished + claimRequests
            }.first { it == recipients }
            requests.claimKeys(
                claimRequests.value.groupBy { it.first }
                    .mapValues { it.value.associate { it.second to KeyAlgorithm.SignedCurve25519 } }
            )
        }
        recipients.associateWith { recipient ->
            val (recipientUserId, recipientDeviceId) = recipient
            async {
                encryptOlm(
                    content = content,
                    recipientUserId = recipientUserId,
                    recipientDeviceId = recipientDeviceId,
                    forceNewSessionWhenNotLastUsedAfter = null,
                    claimOneTimeKey = {
                        claimRequests.update { it + recipient }
                        claimOneTimeKeysResponse.await()
                            .getOneTimeKeyOrThrow(recipientUserId, recipientDeviceId)
                    }
                ).map { requireNotNull(it) }
                    .also { finished.update { it + recipient } }
            }
        }.mapValues { it.value.await() }
    }

    // We need a Mutex to prevent unnecessary parallel key claims as they cannot be reverted like other local operations are.
    private val encryptOlmMutex = KeyedMutex<Pair<UserId, String>>()

    private suspend fun encryptOlm(
        content: EventContent,
        recipientUserId: UserId,
        recipientDeviceId: String,
        forceNewSessionWhenNotLastUsedAfter: Instant?,
        claimOneTimeKey: suspend () -> SignedCurve25519Key,
    ): Result<OlmEncryptedToDeviceEventContent?> =
        encryptOlmMutex.withLock(recipientUserId to recipientDeviceId) {
            runCatchingCancellationAware {
                val ownDeviceKeys = store.getDeviceKeys(ownUserId)?.get(ownDeviceId)
                    ?: throw EncryptOlmError.NoOlmSupported("own device keys not found")
                val recipientDeviceKeys = store.getDeviceKeys(recipientUserId)?.get(recipientDeviceId)
                    ?: throw EncryptOlmError.NoOlmSupported("device keys not found")
                val recipientTrustLevel = store.getTrustLevel(recipientUserId, recipientDeviceId)
                @OptIn(MSC3814::class)
                if (recipientDeviceKeys.signed.dehydrated == true && recipientTrustLevel !is DeviceTrustLevel.CrossSigned)
                    throw EncryptOlmError.DehydratedDeviceNotCrossSigned()
                val recipientIdentityKey = recipientDeviceKeys.get<Curve25519Key>()
                    ?: throw EncryptOlmError.NoOlmSupported("identity key not found")
                val recipientSigningKey = recipientDeviceKeys.get<Ed25519Key>()
                    ?: throw EncryptOlmError.NoOlmSupported("signing key not found")
                val pickleKey =
                    try {
                        driver.key.pickleKey(store.getOlmPickleKey())
                    } catch (exception: CryptoDriverException) {
                        throw EncryptOlmError.CryptoDriverError(exception)
                    }

                atomicUpdateAndRun(
                    getValue = { store.getOlmSessions(recipientIdentityKey.value) },
                    updateValue = { store.updateOlmSessions(recipientIdentityKey.value, it) }
                ) { storedOlmSessions ->
                    val lastUsedOlmStoredOlmSessions = storedOlmSessions.orEmpty().maxByOrNull { it.lastUsedAt }

                    if (lastUsedOlmStoredOlmSessions != null && forceNewSessionWhenNotLastUsedAfter != null && lastUsedOlmStoredOlmSessions.lastUsedAt > forceNewSessionWhenNotLastUsedAfter) {
                        log.debug { "skip creating new olm session, because the session is already recovered (userId=$recipientUserId, deviceId=$recipientDeviceId)" }
                        return@atomicUpdateAndRun AtomicUpdateAndRunResult(
                            result = null,
                            update = storedOlmSessions
                        )
                    }

                    if (lastUsedOlmStoredOlmSessions != null && forceNewSessionWhenNotLastUsedAfter == null) {
                        log.debug { "use existing olm session (userId=$recipientUserId, deviceId=$recipientDeviceId)" }
                        try {
                            val (result, updatedStoredOlmSession) =
                                driver.olm.session.fromPickle(lastUsedOlmStoredOlmSessions.pickled, pickleKey)
                                    .use { session ->
                                        session.encrypt(
                                            content = content,
                                            senderUserId = ownUserId,
                                            senderIdentityKey = ownIdentityKey,
                                            senderSigningKey = ownSigningKey,
                                            senderDeviceKeys = ownDeviceKeys,
                                            recipientUserId = recipientUserId,
                                            recipientIdentityKey = recipientIdentityKey,
                                            recipientSigningKey = recipientSigningKey,
                                        ) to lastUsedOlmStoredOlmSessions.copy(
                                            pickled = session.pickle(pickleKey),
                                            lastUsedAt = clock.now(),
                                        )
                                    }
                            return@atomicUpdateAndRun AtomicUpdateAndRunResult(
                                result = result,
                                update = storedOlmSessions.orEmpty()
                                    .addOrUpdateNewAndRemoveOldSessions(updatedStoredOlmSession)
                            )
                        } catch (exception: CryptoDriverException) {
                            throw EncryptOlmError.CryptoDriverError(exception)
                        }
                    }

                    log.debug { "create new olm session (userId=$recipientUserId, deviceId=$recipientDeviceId)" }

                    val oneTimeKey = claimOneTimeKey()

                    val keyVerifyState =
                        signService.verify(oneTimeKey.value, mapOf(recipientUserId to setOf(recipientSigningKey)))
                    if (keyVerifyState !is VerifyResult.Valid)
                        throw EncryptOlmError.NoOlmSupported("one time key validation failed: $keyVerifyState")

                    try {
                        val (result, newStoredOlmSession) =
                            driver.olm.account.fromPickle(store.getOlmAccount(), pickleKey)
                                .use { olmAccount ->
                                    useAll(
                                        { driver.key.curve25519PublicKey(recipientIdentityKey) },
                                        { driver.key.curve25519PublicKey(oneTimeKey) },
                                    ) { identityKey, oneTimeKey ->
                                        olmAccount.createOutboundSession(
                                            identityKey = identityKey,
                                            oneTimeKey = oneTimeKey,
                                        )
                                    }
                                }.use { session ->
                                    session.encrypt(
                                        content = content,
                                        senderUserId = ownUserId,
                                        senderIdentityKey = ownIdentityKey,
                                        senderSigningKey = ownSigningKey,
                                        senderDeviceKeys = ownDeviceKeys,
                                        recipientUserId = recipientUserId,
                                        recipientIdentityKey = recipientIdentityKey,
                                        recipientSigningKey = recipientSigningKey,
                                    ) to StoredOlmSession(
                                        sessionId = session.sessionId,
                                        senderKey = recipientIdentityKey.value,
                                        pickled = session.pickle(pickleKey),
                                        createdAt = clock.now(),
                                        lastUsedAt = clock.now(),
                                        initiatedByThisDevice = true,
                                    )
                                }
                        return@atomicUpdateAndRun AtomicUpdateAndRunResult(
                            result = result,
                            update = storedOlmSessions.orEmpty().addOrUpdateNewAndRemoveOldSessions(newStoredOlmSession)
                        )
                    } catch (exception: CryptoDriverException) {
                        throw EncryptOlmError.CryptoDriverError(exception)
                    }
                }
            }.onFailure { log.warn(it) { "encrypt olm failed" } }
        }

    private fun Session.encrypt(
        content: EventContent,
        senderUserId: UserId,
        senderIdentityKey: Curve25519Key,
        senderSigningKey: Ed25519Key,
        senderDeviceKeys: SignedDeviceKeys,
        recipientUserId: UserId,
        recipientIdentityKey: Curve25519Key,
        recipientSigningKey: Ed25519Key,
    ): OlmEncryptedToDeviceEventContent {
        @OptIn(ExperimentalSerializationApi::class)
        val serializer = json.serializersModule.getContextual(PlaintextOlmEvent::class)
        val event = PlaintextOlmEvent(
            content = content,
            sender = senderUserId,
            senderKeys = keysOf(senderSigningKey.copy(id = null)),
            senderDeviceKeys = senderDeviceKeys,
            recipient = recipientUserId,
            recipientKeys = keysOf(recipientSigningKey.copy(id = null))
        )
        checkNotNull(serializer)
        val encryptedContent = encrypt(json.encodeToString(serializer, event))
        return OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(
                recipientIdentityKey.value.value to OlmEncryptedToDeviceEventContent.CiphertextInfo.of(
                    encryptedContent
                )
            ),
            senderKey = senderIdentityKey.value,
        )
    }

    override suspend fun recoverOlm(olmRecovery: OlmEncryptionService.OlmRecovery): Result<OlmEncryptedToDeviceEventContent?> =
        encryptOlm(
            content = DummyEventContent,
            recipientUserId = olmRecovery.userId,
            recipientDeviceId = olmRecovery.deviceId,
            forceNewSessionWhenNotLastUsedAfter = olmRecovery.lastTry,
            claimOneTimeKey = {
                requests.claimKeys(mapOf(olmRecovery.userId to mapOf(olmRecovery.deviceId to KeyAlgorithm.SignedCurve25519)))
                    .getOneTimeKeyOrThrow(olmRecovery.userId, olmRecovery.deviceId)
            }
        )

    override suspend fun decryptOlm(
        event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>,
    ): Result<PlaintextOlmEvent<*>> = runCatchingCancellationAware {
        val encryptedContent = event.content
        val senderUserId = event.sender
        val senderIdentityKeyValue = encryptedContent.senderKey
        val ciphertext = encryptedContent.ciphertext[ownIdentityKey.value.value]
            ?: throw DecryptOlmError.SenderDidNotEncryptForThisDevice()
        val senderDeviceKeys = store.findDeviceKeys(senderUserId, senderIdentityKeyValue)
        val pickleKey =
            try {
                driver.key.pickleKey(store.getOlmPickleKey())
            } catch (exception: CryptoDriverException) {
                throw DecryptOlmError.CryptoDriverError(null, exception)
            }

        atomicUpdateAndRun(
            getValue = { store.getOlmSessions(senderIdentityKeyValue) },
            updateValue = { store.updateOlmSessions(senderIdentityKeyValue, it) },
        ) { storedOlmSessions ->
            storedOlmSessions.orEmpty().sortedByDescending { it.lastUsedAt }.forEach { storedSession ->
                val (result, updatedStoredOlmSession) =
                    try {
                        driver.olm.session.fromPickle(storedSession.pickled, pickleKey)
                            .use { olmSession ->
                                val rawPlaintext = try {
                                    driver.olm.message(ciphertext).use(olmSession::decrypt)
                                } catch (_: CryptoDriverException) {
                                    log.debug { "could not decrypt with this session, try another one" }
                                    null
                                } ?: return@forEach
                                decodeAndValidate(
                                    rawPlaintext = rawPlaintext,
                                    senderUserId = senderUserId,
                                    senderIdentityKeyValue = senderIdentityKeyValue,
                                    senderDeviceKeys = senderDeviceKeys,
                                    recipientUserId = ownUserId,
                                    recipientSigningKey = ownSigningKey
                                ) to storedSession.copy(
                                    pickled = olmSession.pickle(pickleKey),
                                    lastUsedAt = clock.now()
                                )
                            }
                    } catch (exception: CryptoDriverException) {
                        throw DecryptOlmError.CryptoDriverError(null, exception)
                    }
                return@atomicUpdateAndRun AtomicUpdateAndRunResult(
                    result = result,
                    update = storedOlmSessions.orEmpty().addOrUpdateNewAndRemoveOldSessions(updatedStoredOlmSession)
                )
            }
            if (ciphertext.type != OlmMessageType.INITIAL_PRE_KEY) {
                val olmRecovery = getOlmRecovery(storedOlmSessions, senderUserId, senderIdentityKeyValue)
                throw DecryptOlmError.NoMatchingOlmSessionFound(olmRecovery)
            }
            if (hasCreatedTooManyOlmInboundSessions(storedOlmSessions))
                throw DecryptOlmError.TooManySessions()

            try {
                log.debug { "decrypt olm event with new session (userId=$senderUserId, senderIdentityKey=$senderIdentityKeyValue)" }

                val (result, newStoredOlmSession) =
                    atomicUpdateAndRun(
                        getValue = { store.getOlmAccount() },
                        updateValue = { store.updateOlmAccount(it) },
                    ) { olmAccount ->
                        driver.olm.account.fromPickle(olmAccount, pickleKey)
                            .use { olmAccount ->
                                val resultAndNewStoredOlmSession = useAll(
                                    { driver.olm.message.preKey(ciphertext.body) },
                                    { driver.key.curve25519PublicKey(senderIdentityKeyValue) },
                                    olmAccount::createInboundSession
                                ).let { (plaintext, session) ->
                                    session.use {
                                        decodeAndValidate(
                                            rawPlaintext = plaintext,
                                            senderUserId = senderUserId,
                                            senderIdentityKeyValue = senderIdentityKeyValue,
                                            senderDeviceKeys = senderDeviceKeys,
                                            recipientUserId = ownUserId,
                                            recipientSigningKey = ownSigningKey,
                                        ) to StoredOlmSession(
                                            sessionId = session.sessionId,
                                            senderKey = senderIdentityKeyValue,
                                            pickled = session.pickle(pickleKey),
                                            createdAt = clock.now(),
                                            lastUsedAt = clock.now(),
                                            initiatedByThisDevice = false,
                                        )
                                    }
                                }
                                AtomicUpdateAndRunResult(
                                    result = resultAndNewStoredOlmSession,
                                    update = olmAccount.pickle(pickleKey)
                                )
                            }
                    }
                return@atomicUpdateAndRun AtomicUpdateAndRunResult(
                    result = result,
                    update = storedOlmSessions.orEmpty().addOrUpdateNewAndRemoveOldSessions(newStoredOlmSession)
                )
            } catch (olmLibraryException: CryptoDriverException) {
                log.debug { "could not decrypt olm event with new session (userId=$senderUserId, senderIdentityKey=$senderIdentityKeyValue), create recovery session. Reason: ${olmLibraryException.message}" }
                val olmRecovery = getOlmRecovery(storedOlmSessions, senderUserId, senderIdentityKeyValue)
                throw DecryptOlmError.CryptoDriverError(olmRecovery, olmLibraryException)
            }
        }
    }.onFailure { log.warn(it) { "decrypt olm failed" } }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun decodeAndValidate(
        rawPlaintext: String,
        senderUserId: UserId,
        senderIdentityKeyValue: Curve25519KeyValue,
        senderDeviceKeys: SignedDeviceKeys?,
        recipientUserId: UserId,
        recipientSigningKey: Ed25519Key,
    ): PlaintextOlmEvent<*> {
        val serializer = json.serializersModule.getContextual(PlaintextOlmEvent::class)
        checkNotNull(serializer)
        val plaintext =
            try {
                json.decodeFromString(serializer, rawPlaintext)
            } catch (exception: SerializationException) {
                throw DecryptOlmError.DeserializationError(exception)
            }

        val senderDeviceKeys =
            senderDeviceKeys?.signed
                ?: plaintext.senderDeviceKeys?.also {
                    val signatureVerification =
                        signService.verify(
                            it,
                            mapOf(senderUserId to setOfNotNull(it.getSelfSigningKey()))
                        )
                    if (signatureVerification != VerifyResult.Valid)
                        throw DecryptOlmError.ValidationFailed("Signatures from device key ${it.signed.deviceId} of $senderUserId were not valid: $signatureVerification")
                }?.signed
                ?: throw DecryptOlmError.ValidationFailed("no sender device keys found")

        @OptIn(MSC3814::class)
        if (senderDeviceKeys.dehydrated == true)
            throw DecryptOlmError.DehydratedDeviceNotAllowed()

        val senderDeviceKeysSigningKey = senderDeviceKeys.keys.keys.filterIsInstance<Ed25519Key>().firstOrNull()
            ?: throw DecryptOlmError.ValidationFailed("no published sender identity key found")
        val senderDeviceKeysIdentityKey =
            senderDeviceKeys.keys.keys.filterIsInstance<Curve25519Key>().firstOrNull()
                ?: throw DecryptOlmError.ValidationFailed("no published sender signing key found")

        return when {
            plaintext.sender != senderUserId ->
                throw DecryptOlmError.ValidationFailed("sender did not match (expected $senderUserId but got ${plaintext.sender})")

            plaintext.recipient != recipientUserId ->
                throw DecryptOlmError.ValidationFailed("recipient did not match (expected $recipientUserId but got ${plaintext.recipient})")

            plaintext.recipientKeys.filterIsInstance<Ed25519Key>()
                .firstOrNull()?.value != recipientSigningKey.value ->
                throw DecryptOlmError.ValidationFailed("recipientKeys did not match (expected $recipientSigningKey but got ${plaintext.recipientKeys})")

            senderDeviceKeysSigningKey.value != plaintext.senderKeys.filterIsInstance<Ed25519Key>()
                .firstOrNull()?.value ->
                throw DecryptOlmError.ValidationFailed("senderKeys did not match (expected $senderDeviceKeysSigningKey but got ${plaintext.senderKeys})")

            senderDeviceKeysIdentityKey.value != senderIdentityKeyValue ->
                throw DecryptOlmError.ValidationFailed("senderKeys did not match (expected $senderDeviceKeysIdentityKey but got ${senderIdentityKeyValue})")

            senderDeviceKeys.userId != plaintext.sender ->
                throw DecryptOlmError.ValidationFailed("wrong device keys: sender did not match (expected $senderUserId but got ${senderDeviceKeys.userId})")

            else -> plaintext
        }
    }

    private suspend fun getOlmRecovery(
        storedSessions: Set<StoredOlmSession>?,
        senderUserId: UserId,
        senderIdentityKey: Curve25519KeyValue,
    ): OlmEncryptionService.OlmRecovery? {
        val deviceId = store.findDeviceKeys(senderUserId, senderIdentityKey)?.signed?.deviceId
        return if (!hasCreatedTooManyOlmOutboundSessions(storedSessions) && deviceId != null) {
            OlmEncryptionService.OlmRecovery(senderUserId, deviceId, clock.now())
        } else {
            log.debug { "already created a recovery session recently and therefore skip creating a new one" }
            null
        }
    }

    private fun Result<ClaimKeys.Response>.getOneTimeKeyOrThrow(
        recipientUserId: UserId,
        recipientDeviceId: String
    ): SignedCurve25519Key {
        val response = fold(
            onFailure = { throw EncryptOlmError.NetworkError(it) },
            onSuccess = { it }
        )

        when (val failures = response.failures[recipientUserId.domain]) {
            is JsonArray if failures.isNotEmpty() -> throw RemoteHomeserverNotReachable(failures.toString())
            is JsonObject if failures.isNotEmpty() -> throw RemoteHomeserverNotReachable(failures.toString())
            is JsonPrimitive if failures.content.isNotEmpty() -> throw RemoteHomeserverNotReachable(failures.toString())
            else -> {}
        }
        return response.oneTimeKeys[recipientUserId]?.get(recipientDeviceId)?.keys?.firstOrNull() as? SignedCurve25519Key
            ?: throw EncryptOlmError.NoOlmSupported("one time key not found")
    }

    private fun Set<StoredOlmSession>.addOrUpdateNewAndRemoveOldSessions(newSession: StoredOlmSession): Set<StoredOlmSession> {
        val newSessions = filterNot { it.sessionId == newSession.sessionId }.toSet() + newSession
        return if (newSessions.size > 9) newSessions.sortedBy { it.lastUsedAt }.drop(1).toSet()
        else newSessions
    }

    private fun hasCreatedTooManyOlmInboundSessions(storedSessions: Set<StoredOlmSession>?): Boolean {
        val now = clock.now()
        return (storedSessions?.size ?: 0) >= 3 && storedSessions
            ?.sortedByDescending { it.createdAt }
            ?.takeLast(3)
            ?.map {
                check(it.createdAt <= now)
                (it.createdAt + 1.hours) >= now
            }
            ?.all { it } == true
    }

    private fun hasCreatedTooManyOlmOutboundSessions(storedSessions: Set<StoredOlmSession>?): Boolean {
        val now = clock.now()
        val lastSessionCreatedAt = storedSessions
            ?.filter { it.initiatedByThisDevice }
            ?.minByOrNull { it.createdAt }
            ?.createdAt
        return lastSessionCreatedAt != null && (now - lastSessionCreatedAt) < 10.seconds
    }


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

