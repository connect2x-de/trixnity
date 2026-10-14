package de.connect2x.trixnity.client.rtc

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.flattenNotNull
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.RoomTimelineStore
import de.connect2x.trixnity.client.utils.retry
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.PerMemberRtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryptionKeyEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventContainer
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import de.connect2x.trixnity.crypto.olm.OlmEventHandler
import de.connect2x.trixnity.utils.encodeUnpaddedBase64
import de.connect2x.trixnity.utils.nextString
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val log = Logger("de.connect2x.trixnity.client.rtc.PerMemberRtcSlotMembershipEncryptionHandler")

@MSC4143
data class PerMemberRtcSlotMembershipEncryptionInfo(
    val receivedKeys: Map<Pair<UserId, RtcMemberId>, List<RtcEncryptionKeyEventContent>>,
    val ownActiveKey: RtcEncryptionKeyEventContent,
) : RtcSlotMembershipEncryptionInfo

@MSC4143
data class PerMemberRtcSlotMembershipEncryptionConfig(
    val useKeyDelay: Duration = 5.seconds,
    val keyRotationGracePeriod: Duration = 10.seconds,
    val periodicKeyRotation: Duration = 10.minutes,
    val receivedKeysBuffer: Int = 3,
) : RtcSlotMembershipEncryptionConfig

@MSC4143
abstract class PerMemberRtcSlotMembershipEncryptionHandler(
    private val roomTimelineStore: RoomTimelineStore,
    private val keyStore: KeyStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val olmEventHandler: OlmEventHandler,
    private val olmEncryptionService: OlmEncryptionService,
    private val api: MatrixClientServerApiClient,
    private val currentSyncState: CurrentSyncState,
    private val clock: Clock,
    private val userInfo: UserInfo,
) : RtcSlotMembershipEncryptionHandler {
    override fun supports(encryption: RtcEncryption, transports: RtcMemberEventContent.Join.RtcTransports?): Boolean =
        encryption is PerMemberRtcEncryption

    private data class ReceivedKeysKey(
        val userId: UserId,
        val memberId: RtcMemberId,
        val senderKey: KeyValue.Curve25519KeyValue,
    )

    private data class ReceivedKeys(val content: RtcEncryptionKeyEventContent, val receivedAt: Instant)

    private data class OwnKey(
        val content: RtcEncryptionKeyEventContent,
        val distributedTo: Set<Pair<UserId, String>>,
        val createdAt: Instant,
        val invalidated: Boolean,
    )

    private data class OwnKeys(val current: OwnKey, val next: OwnKey?)

    override fun getEncryptionInfo(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        encryption: RtcEncryption,
        transports: RtcMemberEventContent.Join.RtcTransports?,
        config: Map<RtcEncryption, RtcSlotMembershipEncryptionConfig>,
        memberEvents: Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>,
    ): Flow<RtcSlotMembershipEncryptionInfo> = callbackFlow {
        log.debug { "initialize encryption info for $slotId in $roomId" }

        // TODO this should be cleaned (e.g. when a received key hasn't been used in a while or never)
        val receivedKeys = MutableStateFlow<Map<ReceivedKeysKey, List<ReceivedKeys>>>(emptyMap())
        val mediaKey =
            RtcEncryptionKeyEventContent.MediaKey(key = generateEphemeralMediaKey().encodeUnpaddedBase64(), index = 0)
        val ownKeys =
            MutableStateFlow<OwnKeys>(
                OwnKeys(
                    current =
                        OwnKey(
                            content =
                                RtcEncryptionKeyEventContent(
                                    roomId = roomId,
                                    memberId = memberId,
                                    member = RtcEncryptionKeyEventContent.Member(memberId, userInfo.deviceId),
                                    mediaKey = mediaKey,
                                    keys = mediaKey,
                                ),
                            distributedTo = emptySet(),
                            createdAt = clock.now(),
                            invalidated = false,
                        ),
                    next = null,
                )
            )

        val config =
            (config[PerMemberRtcEncryption] as? PerMemberRtcSlotMembershipEncryptionConfig)
                ?: PerMemberRtcSlotMembershipEncryptionConfig()

        val unsubscriber = olmEventHandler.subscribe { subscribeOlmEvents(roomId, it, receivedKeys, config) }
        invokeOnClose { unsubscriber() }
        launch {
            keyRotationAndDistribution(
                roomId = roomId,
                slotId = slotId,
                memberId = memberId,
                config = config,
                memberEvents = memberEvents,
                ownKeys = ownKeys,
            )
        }
        combine(memberEvents, receivedKeys) { memberEvents, receivedKeys ->
                memberEvents.mapValues { (key, memberEvent) ->
                    memberEvent.resolveSenderIdentity().map { memberEventAndSenderKey ->
                        if (memberEventAndSenderKey == null) return@map emptyList()
                        val key =
                            ReceivedKeysKey(
                                userId = key.first,
                                memberId = key.second,
                                senderKey = memberEventAndSenderKey,
                            )
                        receivedKeys[key]?.map { it.content }.orEmpty()
                    }
                }
            }
            .flattenNotNull()
            .combine(ownKeys.map { it.current.content }.distinctUntilChanged()) { receivedKeys, ownActiveKey ->
                receivedKeys to ownActiveKey
            }
            .collect { (receivedKeys, ownActiveKey) ->
                send(PerMemberRtcSlotMembershipEncryptionInfo(receivedKeys = receivedKeys, ownActiveKey = ownActiveKey))
            }
    }

    private sealed interface InvalidationTrigger {
        val join: Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>

        data class MembershipChanges(
            override val join:
                Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>,
            val joinDelta: Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>,
            val leaveDelta: Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>,
        ) : InvalidationTrigger

        data class PeriodicKeyRotation(
            override val join:
                Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>
        ) : InvalidationTrigger
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun keyRotationAndDistribution(
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        config: PerMemberRtcSlotMembershipEncryptionConfig,
        memberEvents: Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>,
        ownKeys: MutableStateFlow<OwnKeys>,
    ) = coroutineScope {
        data class ScanResult(
            val previous: Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>,
            val current: Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>,
        )

        launch {
            memberEvents
                .map { it.filterKeys { it.first != userInfo.userId || it.second != memberId } }
                .scan(ScanResult(emptyMap(), emptyMap())) { a, b -> ScanResult(previous = a.current, current = b) }
                .map { (previous, current) ->
                    val (previousJoin, previousLeave) =
                        previous.entries
                            .partition { it.value?.content is RtcMemberEventContent.Join }
                            .let {
                                it.first.associate { it.key to it.value } to it.second.associate { it.key to it.value }
                            }
                    val (currentJoin, currentLeave) =
                        current.entries
                            .partition { it.value?.content is RtcMemberEventContent.Join }
                            .let {
                                it.first.associate { it.key to it.value } to it.second.associate { it.key to it.value }
                            }
                    InvalidationTrigger.MembershipChanges(
                        join = currentJoin,
                        joinDelta = currentJoin - previousJoin.keys,
                        leaveDelta = currentLeave - previousLeave.keys,
                    )
                }
                .transformLatest { membershipChanges ->
                    emit(membershipChanges)
                    while (currentCoroutineContext().isActive) {
                        delay(config.periodicKeyRotation)
                        emit(InvalidationTrigger.PeriodicKeyRotation(membershipChanges.join))
                    }
                }
                .collect { invalidationTrigger ->
                    when (invalidationTrigger) {
                        is InvalidationTrigger.MembershipChanges -> {
                            if (invalidationTrigger.leaveDelta.isNotEmpty()) {
                                ownKeys.invalidateWithRecipients(
                                    recipients = invalidationTrigger.leaveDelta.resolveUserIdAndDeviceIds()
                                )
                            }
                            if (invalidationTrigger.joinDelta.isNotEmpty()) {
                                ownKeys.invalidateAfterGracePeriod(config.keyRotationGracePeriod)
                            }
                        }
                        is InvalidationTrigger.PeriodicKeyRotation -> {
                            ownKeys.invalidateWhenNotInvalidated()
                        }
                    }

                    ownKeys.createNextKeyOnInvalidation(roomId, memberId)
                    ownKeys.distributeKey(roomId, slotId, invalidationTrigger.join.resolveUserIdAndDeviceIds())
                }
        }
        launch {
            while (currentCoroutineContext().isActive) {
                ownKeys.first { it.current.invalidated }
                log.debug { "wait ${config.useKeyDelay} before using new key" }
                delay(config.useKeyDelay)
                ownKeys.rotate(roomId, memberId)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ClientEvent.RoomEvent.MessageEvent<*>.resolveSenderIdentity(): Flow<KeyValue.Curve25519KeyValue?> =
        roomTimelineStore.get(id, roomId).flatMapLatest {
            val sessionId =
                (it?.event?.content as? EncryptedMessageEventContent.MegolmEncryptedMessageEventContent)?.sessionId
                    ?: return@flatMapLatest flowOf(null)
            olmCryptoStore.getInboundMegolmSession(sessionId, roomId).map { inboundMegolmSession ->
                inboundMegolmSession?.senderKey
            }
        }

    private suspend fun Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<*>?>
        .resolveUserIdAndDeviceIds(): Set<Pair<UserId, String>> =
        values
            .mapNotNull { event ->
                val senderKey = event?.resolveSenderIdentity()?.first() ?: return@mapNotNull null
                val deviceKeys =
                    keyStore.getDeviceKeys(event.sender).first()?.values?.find {
                        it.value.signed.keys.keys.any { key -> key.value == senderKey }
                    } ?: return@mapNotNull null
                deviceKeys.value.signed.userId to deviceKeys.value.signed.deviceId
            }
            .toSet()

    private fun subscribeOlmEvents(
        roomId: RoomId,
        container: DecryptedOlmEventContainer,
        receivedKeys: MutableStateFlow<Map<ReceivedKeysKey, List<ReceivedKeys>>>,
        config: PerMemberRtcSlotMembershipEncryptionConfig,
    ) {
        val content = (container.decrypted.content as? RtcEncryptionKeyEventContent) ?: return
        if (content.roomId != roomId) return
        val key =
            ReceivedKeysKey(
                userId = container.decrypted.sender,
                memberId = content.memberId ?: content.member?.id ?: return,
                senderKey = container.encrypted.content.senderKey,
            )
        receivedKeys.update { old ->
            val list = old[key].orEmpty().plus(ReceivedKeys(content = content, receivedAt = clock.now()))
            val dropSize = (list.size - config.receivedKeysBuffer).coerceAtLeast(0)
            old + (key to list.drop(dropSize))
        }
    }

    private suspend fun MutableStateFlow<OwnKeys>.distributeKey(
        roomId: RoomId,
        slotId: RtcSlotId,
        recipients: Set<Pair<UserId, String>>,
    ) {
        val ownKey = value.next ?: value.current
        val recipients = recipients - ownKey.distributedTo - (userInfo.userId to userInfo.deviceId)
        if (recipients.isEmpty()) return
        currentSyncState.retry(
            onError = { error, delay ->
                log.warn(error) { "failed to distribute key or $slotId in $roomId, try again in $delay" }
            }
        ) {
            val eventsToSend =
                olmEncryptionService
                    .encryptOlm(ownKey.content, recipients)
                    .mapNotNull { (recipient, encryptOlmResult) ->
                        encryptOlmResult
                            .onFailure {
                                val e = it as? EncryptOlmError
                                when (e) {
                                    is EncryptOlmError.CryptoDriverError -> throw e
                                    is EncryptOlmError.NetworkError -> throw e

                                    is EncryptOlmError.DehydratedDeviceNotCrossSigned -> {
                                        log.debug {
                                            "will not distribute key to $recipient, because dehydrated device not cross signed"
                                        }
                                    }

                                    is EncryptOlmError.NoOlmSupported -> {
                                        log.debug { "will not distribute key to $recipient, because olm not supported" }
                                    }

                                    is EncryptOlmError.RemoteHomeserverNotReachable -> {
                                        log.warn {
                                            "will not distribute key to $recipient, because remote homeserver not reachable and therefore new olm session could not be created"
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

            if (eventsToSend.isEmpty()) {
                return@retry
            }
            log.debug {
                "distribute key for $slotId in $roomId to devices: ${eventsToSend.mapValues { it.value.keys }}"
            }
            api.user.sendToDevice(eventsToSend, SecureRandom.nextString(22)).getOrThrow()
        }
        update { old ->
            old.copy(
                current =
                    if (old.current == ownKey) old.current.copy(distributedTo = old.current.distributedTo + recipients)
                    else old.current,
                next =
                    if (old.next == ownKey) old.next.copy(distributedTo = old.next.distributedTo + recipients)
                    else old.next,
            )
        }
    }

    private fun MutableStateFlow<OwnKeys>.invalidate(predicate: (OwnKey) -> Boolean) = updateAndGet { old ->
        old.copy(
            current = old.current.copy(invalidated = old.current.invalidated || predicate(old.current)),
            next = old.next?.copy(invalidated = old.next.invalidated || predicate(old.next)),
        )
    }

    private fun MutableStateFlow<OwnKeys>.invalidateWithRecipients(recipients: Set<Pair<UserId, String>>) =
        invalidate { ownKey -> ownKey.distributedTo.any { it in recipients } }
            .also {
                log.debug {
                    "invalidated because of left recipients: current=${it.current.invalidated} next=${it.next?.invalidated}"
                }
            }

    private fun MutableStateFlow<OwnKeys>.invalidateAfterGracePeriod(gracePeriod: Duration) =
        invalidate { ownKey -> (ownKey.createdAt + gracePeriod) < clock.now() }
            .also {
                log.debug {
                    "invalidated because of joined recipients: current=${it.current.invalidated} next=${it.next?.invalidated}"
                }
            }

    private fun MutableStateFlow<OwnKeys>.invalidateWhenNotInvalidated() =
        invalidate { ownKey -> ownKey.invalidated.not() }
            .also {
                log.debug {
                    "invalidated because of periodic rotation: current=${it.current.invalidated} next=${it.next?.invalidated}"
                }
            }

    private suspend fun MutableStateFlow<OwnKeys>.createNextKeyOnInvalidation(roomId: RoomId, memberId: RtcMemberId) {
        update { old ->
            if (old.current.invalidated && old.next == null) {
                log.debug { "create new next key because of invalidation" }
                old.copy(current = old.current, next = old.current.createNextKey(roomId, memberId))
            } else old
        }
    }

    private suspend fun OwnKey.createNextKey(roomId: RoomId, memberId: RtcMemberId): OwnKey {
        val nextIndex = (requireNotNull(content.mediaKey ?: content.keys).index + 1).takeIf { it <= 255 } ?: 0
        val mediaKey =
            RtcEncryptionKeyEventContent.MediaKey(
                key = generateEphemeralMediaKey().encodeUnpaddedBase64(),
                index = nextIndex,
            )
        return OwnKey(
            content =
                RtcEncryptionKeyEventContent(
                    roomId = roomId,
                    memberId = memberId,
                    member = RtcEncryptionKeyEventContent.Member(memberId, userInfo.deviceId),
                    mediaKey = mediaKey,
                    keys = mediaKey,
                ),
            createdAt = clock.now(),
            distributedTo = setOf(),
            invalidated = false,
        )
    }

    private suspend fun MutableStateFlow<OwnKeys>.rotate(roomId: RoomId, memberId: RtcMemberId) {
        val new = updateAndGet { old ->
            old.copy(
                current = old.next ?: old.current,
                next = if (old.next?.invalidated == true) old.next.createNextKey(roomId, memberId) else null,
            )
        }
        log.debug { "rotated key, createdNext=${new.next!=null}" }
    }

    internal abstract suspend fun generateEphemeralMediaKey(): ByteArray
}
