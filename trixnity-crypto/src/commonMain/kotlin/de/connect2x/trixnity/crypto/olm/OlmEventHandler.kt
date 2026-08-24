package de.connect2x.trixnity.crypto.olm

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.core.ClientEventEmitter
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.Unsubscriber
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.Event
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent
import de.connect2x.trixnity.core.model.events.m.RoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.subscribe
import de.connect2x.trixnity.core.subscribeEvent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.crypto.driver.useAll
import de.connect2x.trixnity.crypto.invoke
import de.connect2x.trixnity.crypto.sign.SignService
import de.connect2x.trixnity.utils.AtomicUpdateAndRunResult
import de.connect2x.trixnity.utils.atomicUpdateAndRun
import de.connect2x.trixnity.utils.nextString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val log = Logger("de.connect2x.trixnity.crypto.olm.OlmEventHandler")

data class DecryptedOlmEventContainer(
    val encrypted: Event<OlmEncryptedToDeviceEventContent>,
    val decrypted: PlaintextOlmEvent<*>,
)

typealias DecryptedOlmEventSubscriber = suspend (DecryptedOlmEventContainer) -> Unit

interface OlmEventHandler : EventHandler {
    fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber
}

class OlmEventHandlerImpl(
    private val userInfo: UserInfo,
    private val eventEmitter: ClientEventEmitter<*>,
    private val olmKeysChangeEmitter: OlmKeysChangeEmitter,
    private val signService: SignService,
    private val olmEncryptionService: OlmEncryptionService,
    private val requestHandler: OlmEventHandlerRequestHandler,
    private val store: OlmStore,
    private val clock: Clock,
    private val driver: CryptoDriver,
) : OlmEventHandler {
    private val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmKeysChangeEmitter.subscribeOneTimeKeysCount(::handleOlmKeysChange).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEventList(subscriber = ::handleMemberEvents).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEvent(subscriber = ::handleHistoryVisibility).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEventList(Priority.TO_DEVICE_EVENTS, ::handleOlmEvents).unsubscribeOnCompletion(scope)
        eventEmitter.subscribe(Priority.LAST, ::forgetOldFallbackKey).unsubscribeOnCompletion(scope)
        subscribe(::handleOlmEncryptedRoomKeyEventContent).unsubscribeOnCompletion(scope)
        scope.launch { forgetOldFallbackKey() }
    }

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber {
        eventSubscribers.update { it + eventSubscriber }
        return { eventSubscribers.update { it - eventSubscriber } }
    }

    internal suspend fun handleOlmEvents(events: List<ToDeviceEvent<OlmEncryptedToDeviceEventContent>>) =
        coroutineScope {
            val olmRecoveries =
                events
                    .groupBy { it.sender to it.content.senderKey }
                    .map { (_, events) ->
                        async {
                            events
                                .mapNotNull { event ->
                                    val decryptOlmResult = olmEncryptionService.decryptOlm(event)
                                    val olmRecovery =
                                        (decryptOlmResult.exceptionOrNull() as? OlmEncryptionService.DecryptOlmError)
                                            ?.olmRecovery
                                    if (olmRecovery != null) return@mapNotNull olmRecovery
                                    val decryptedEvent = decryptOlmResult.getOrNull()
                                    if (decryptedEvent != null) {
                                        eventSubscribers.value.forEach {
                                            it(DecryptedOlmEventContainer(event, decryptedEvent))
                                        }
                                    }
                                    null
                                }
                                .lastOrNull()
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
            if (olmRecoveries.isNotEmpty()) {
                val olmRecoveryEvents =
                    olmRecoveries
                        .groupBy { olmRecovery -> olmRecovery.userId }
                        .mapValues { (_, olmRecoveriesByUser) ->
                            coroutineScope {
                                olmRecoveriesByUser
                                    .map {
                                        async {
                                            val olmRecoveryEvent =
                                                olmEncryptionService
                                                    .recoverOlm(it)
                                                    .onFailure { exception ->
                                                        when (exception as? OlmEncryptionService.EncryptOlmError) {
                                                            is OlmEncryptionService.EncryptOlmError.NetworkError ->
                                                                throw exception
                                                            is OlmEncryptionService.EncryptOlmError.RemoteHomeserverNotReachable,
                                                            is OlmEncryptionService.EncryptOlmError.CryptoDriverError,
                                                            is OlmEncryptionService.EncryptOlmError.DehydratedDeviceNotCrossSigned,
                                                            is OlmEncryptionService.EncryptOlmError.NoOlmSupported,
                                                            null -> {
                                                                log.warn(exception) {
                                                                    "exception while recovering olm session"
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .getOrNull() ?: return@async null
                                            it.deviceId to olmRecoveryEvent
                                        }
                                    }
                                    .awaitAll()
                                    .filterNotNull()
                                    .toMap()
                            }
                        }
                        .filterValues { it.isNotEmpty() }
                requestHandler.sendToDevice(olmRecoveryEvents, SecureRandom.nextString(22)).getOrThrow()
            }
        }

    internal suspend fun forgetOldFallbackKey() {
        val forgetFallbackKeyAfter = store.getForgetFallbackKeyAfter()
        if (forgetFallbackKeyAfter != null && forgetFallbackKeyAfter < clock.now()) {
            val pickleKey = store.getOlmPickleKey()
            log.debug { "forget old fallback key" }
            store.updateOlmAccount { pickledOlmAccount ->
                val pickleKey = driver.key.pickleKey(pickleKey)

                driver.olm.account.fromPickle(pickledOlmAccount, pickleKey).use { olmAccount ->
                    olmAccount.forgetFallbackKey()
                    olmAccount.pickle(pickleKey)
                }
            }
            store.updateForgetFallbackKeyAfter { null }
        }
    }

    internal suspend fun handleOlmKeysChange(change: OlmKeysChange) {
        val oneTimeKeysCount = change.oneTimeKeysCount
        val fallbackKeyTypes = change.fallbackKeyTypes
        val pickleKey = driver.key.pickleKey(store.getOlmPickleKey())

        log.trace {
            "handle change of own olm keys server count (oneTimeKeysCount=$oneTimeKeysCount, fallbackKeyTypes=$fallbackKeyTypes)"
        }
        val (newOneTimeKeys, newFallbackKeys) =
            atomicUpdateAndRun(getValue = { store.getOlmAccount() }, updateValue = { store.updateOlmAccount(it) }) {
                pickledOlmAccount ->
                driver.olm.account.fromPickle(pickledOlmAccount, pickleKey).use { olmAccount ->
                    var newOneTimeKeys = olmAccount.oneTimeKeys.toCurve25519Keys()
                    if (newOneTimeKeys != null) log.debug { "found one time keys marked as unpublished" }
                    if (oneTimeKeysCount != null && newOneTimeKeys.isNullOrEmpty()) {
                        val generateOneTimeKeysCount =
                            (olmAccount.maxNumberOfOneTimeKeys - (oneTimeKeysCount[KeyAlgorithm.SignedCurve25519] ?: 0))
                                .coerceAtLeast(0)
                        if (generateOneTimeKeysCount > 0) {
                            val generateOneTimeKeysCountWithBuffer =
                                generateOneTimeKeysCount + olmAccount.maxNumberOfOneTimeKeys / 2
                            log.debug { "generate $generateOneTimeKeysCountWithBuffer new one time key" }
                            olmAccount.generateOneTimeKeys(generateOneTimeKeysCountWithBuffer)
                            newOneTimeKeys = olmAccount.oneTimeKeys.toCurve25519Keys()
                        }
                    }

                    var newFallbackKeys = olmAccount.fallbackKey?.let(::mapOf)?.toCurve25519Keys(fallback = true)
                    if (newFallbackKeys != null) log.debug { "found fallback key marked as unpublished" }
                    if (
                        newFallbackKeys.isNullOrEmpty() &&
                            fallbackKeyTypes != null &&
                            fallbackKeyTypes.contains(KeyAlgorithm.SignedCurve25519).not()
                    ) {
                        log.debug { "generate new fallback key" }
                        olmAccount.generateFallbackKey()
                        newFallbackKeys = olmAccount.fallbackKey?.let(::mapOf)?.toCurve25519Keys(fallback = true)
                    }
                    AtomicUpdateAndRunResult(
                        result = newOneTimeKeys to newFallbackKeys,
                        update = olmAccount.pickle(pickleKey),
                    )
                }
            }
        if (newOneTimeKeys != null || newFallbackKeys != null) {
            log.debug {
                "upload ${newOneTimeKeys?.size ?: 0} one time keys and ${newFallbackKeys?.size ?: 0} fallback keys."
            }
            requestHandler.setOneTimeKeys(oneTimeKeys = newOneTimeKeys, fallbackKeys = newFallbackKeys).onFailure {
                if (it is MatrixServerException && it.statusCode.value in (400 until 500)) {
                    log.error(it) {
                        "Possibly detected OTKs on the server with key ids that have already been uploaded. " +
                            "This is a severe misbehavior and must be fixed in Trixnity!!!"
                    }
                } else throw it
            }

            store.updateOlmAccount { pickledOlmAccount ->
                driver.olm.account.fromPickle(pickledOlmAccount, pickleKey).use { olmAccount ->
                    log.debug { "mark keys as published" }
                    olmAccount.markKeysAsPublished()
                    olmAccount.pickle(pickleKey)
                }
            }

            newFallbackKeys // we can forget the old fallback key, when we had to generate a new one and successfully
                // set it
                ?.also {
                    val forgetAfter = clock.now() + 1.hours
                    log.debug { "mark fallback key to be forget after $forgetAfter" }
                    store.updateForgetFallbackKeyAfter { clock.now() + 1.hours }
                }
        }
        log.trace { "finished handle change of own olm keys server count" }
    }

    private suspend fun Map<String, Curve25519PublicKey>.toCurve25519Keys(fallback: Boolean? = null) =
        map { (keyId, key) ->
                signService.signCurve25519Key(
                    keyId = keyId,
                    keyValue = key.use(Curve25519PublicKey::base64),
                    fallback = fallback,
                )
            }
            .toSet()
            .let(::Keys)
            .ifEmpty { null }

    internal suspend fun handleOlmEncryptedRoomKeyEventContent(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is RoomKeyEventContent) {
            log.debug { "got inbound megolm session for room ${content.roomId}" }
            val senderSigningKey = event.decrypted.senderKeys.keys.filterIsInstance<Ed25519Key>().firstOrNull()
            if (senderSigningKey == null) {
                log.warn { "ignore inbound megolm session because it did not contain any sender signing key" }
                return
            }
            try {
                val (firstKnownIndex, pickledSession) =
                    useAll(
                        { driver.megolm.sessionKey(content.sessionKey) },
                        { driver.megolm.inboundGroupSession(it) },
                    ) { _, inboundGroupSession ->
                        inboundGroupSession.firstKnownIndex.toLong() to
                            inboundGroupSession.pickle(driver.key.pickleKey(store.getOlmPickleKey()))
                    }

                store.updateInboundMegolmSession(content.sessionId, content.roomId) {
                    if (it != null && it.firstKnownIndex <= firstKnownIndex) it
                    else
                        StoredInboundMegolmSession(
                            senderKey = event.encrypted.content.senderKey,
                            senderSigningKey = senderSigningKey.value,
                            sessionId = content.sessionId,
                            roomId = content.roomId,
                            firstKnownIndex = firstKnownIndex,
                            hasBeenBackedUp = false,
                            isTrusted = true,
                            forwardingCurve25519KeyChain = emptyList(),
                            pickled = pickledSession,
                        )
                }
            } catch (exception: CryptoDriverException) {
                log.warn { "ignore inbound megolm session due to: ${exception.message}" }
                null
            }
        }
    }

    internal suspend fun handleMemberEvents(events: List<StateEvent<MemberEventContent>>) = coroutineScope {
        events.forEach { event ->
            val roomId = event.roomId
            val userId = UserId(event.stateKey)
            if (userId != userInfo.userId && store.getRoomEncryptionAlgorithm(roomId) == EncryptionAlgorithm.Megolm) {
                val membership = event.content.membership
                val membershipsAllowedToReceiveKey = store.getHistoryVisibility(roomId).membershipsAllowedToReceiveKey
                if (membershipsAllowedToReceiveKey.contains(membership)) {
                    val devices = store.getDeviceKeys(userId)?.keys
                    store.updateOutboundMegolmSession(roomId) {
                        if (it != null) {
                            log.debug {
                                "add new devices of $userId to megolm session of $roomId, because new membership does allow to share key"
                            }
                            if (!devices.isNullOrEmpty()) it.copy(newDevices = it.newDevices + (userId to devices))
                            else it
                        } else null
                    }
                } else {
                    log.debug { "reset megolm session of $roomId, because new membership does not allow share key" }
                    store.updateOutboundMegolmSession(roomId) { null }
                }
            }
        }
    }

    internal suspend fun handleHistoryVisibility(event: StateEvent<HistoryVisibilityEventContent>) {
        log.debug { "reset megolm session, because visibility has changed in ${event.roomId}" }
        if (store.getRoomEncryptionAlgorithm(event.roomId) == EncryptionAlgorithm.Megolm)
            store.updateOutboundMegolmSession(event.roomId) { null }
    }
}
