package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.getInMemoryRoomTimelineStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.OlmEventHandlerMock
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.user.SendToDevice
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.PerMemberRtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryptionKeyEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventContainer
import de.connect2x.trixnity.crypto.olm.InboundMegolmSessionSource
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import de.connect2x.trixnity.utils.encodeUnpaddedBase64
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject

@OptIn(MSC4143::class, MSC4193::class)
class PerMemberRtcSlotMembershipEncryptionHandlerTest : TrixnityBaseTest() {

    private val tm = NoOpStoreTransactionManager
    private val config =
        PerMemberRtcSlotMembershipEncryptionConfig(
            useKeyDelay = 5.seconds,
            keyRotationGracePeriod = 10.seconds,
            periodicKeyRotation = 30.seconds,
            receivedKeysBuffer = 2,
        )
    private val ownUserId = UserId("us", "server")
    private val ownDeviceId = "device"
    private val ownMemberId = RtcMemberId("own-member")
    private val roomId = RoomId("!room:server")
    private val slotId = RtcSlotId("slot")

    private val alice = UserId("alice", "server")
    private val aliceMemberId = RtcMemberId("alice-member")
    private val aliceDevice = "ALICEDEVICE"
    private val aliceSenderKey = Curve25519KeyValue("aliceSenderKey")
    private val aliceSessionId = "aliceSession"
    private val aliceEventId = EventId("\$aliceEvent")
    private val aliceLeaveSessionId = "aliceLeaveSession"
    private val aliceLeaveEventId = EventId("\$aliceLeaveEvent")

    private val bob = UserId("bob", "server")
    private val bobMemberId = RtcMemberId("bob-member")
    private val bobDevice = "BOBDEVICE"
    private val bobSenderKey = Curve25519KeyValue("bobSenderKey")
    private val bobSessionId = "bobSession"
    private val bobEventId = EventId("\$bobEvent")

    private var sentToDevice: Map<UserId, Map<String, ToDeviceEventContent>>? = null

    private val roomTimelineStore = getInMemoryRoomTimelineStore {
        tm.writeTransaction {
            listOf(
                    Triple(aliceEventId, alice, aliceSessionId),
                    Triple(aliceLeaveEventId, alice, aliceLeaveSessionId),
                    Triple(bobEventId, bob, bobSessionId),
                )
                .forEach { (eventId, userId, sessionId) ->
                    add(
                        TimelineEvent(
                            event =
                                ClientEvent.RoomEvent.MessageEvent(
                                    EncryptedMessageEventContent.MegolmEncryptedMessageEventContent(
                                        ciphertext = MegolmMessageValue("cipher"),
                                        sessionId = sessionId,
                                    ),
                                    eventId,
                                    userId,
                                    roomId,
                                    1234,
                                )
                        )
                    )
                }
        }
    }
    private val keyStore = getInMemoryKeyStore {
        tm.writeTransaction {
            saveDeviceKeys(alice, mapOf(aliceDevice to deviceKeys(alice, aliceDevice, aliceSenderKey)))
            saveDeviceKeys(bob, mapOf(bobDevice to deviceKeys(bob, bobDevice, bobSenderKey)))
        }
    }
    private val olmCryptoStore = getInMemoryOlmStore {
        tm.writeTransaction {
            listOf(
                    Triple(aliceSessionId, aliceSenderKey, aliceEventId),
                    Triple(aliceLeaveSessionId, aliceSenderKey, aliceLeaveEventId),
                    Triple(bobSessionId, bobSenderKey, bobEventId),
                )
                .forEach { (sessionId, senderKey, _) ->
                    updateInboundMegolmSession(sessionId, roomId) {
                        StoredInboundMegolmSession(
                            senderKey = senderKey,
                            senderSigningKey = Ed25519KeyValue("senderSigning"),
                            sessionId = sessionId,
                            roomId = roomId,
                            firstKnownIndex = 0,
                            hasBeenBackedUp = false,
                            source = InboundMegolmSessionSource.Creator,
                            sharedHistory = false,
                            pickled = "pickle",
                        )
                    }
                }
        }
    }
    private val olmEventHandler = OlmEventHandlerMock()
    private val olmEncryptionService = OlmEncryptionServiceMock()
    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)
    private val memberEvents =
        MutableStateFlow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>(
                emptyMap()
            )
            .apply { scheduleSetup { value = emptyMap() } }
    private var mediaKeyCounter = 0

    private inner class TestPerMemberRtcSlotMembershipEncryptionHandler :
        PerMemberRtcSlotMembershipEncryptionHandler(
            roomTimelineStore = roomTimelineStore,
            keyStore = keyStore,
            olmCryptoStore = olmCryptoStore,
            olmEventHandler = olmEventHandler,
            olmEncryptionService = olmEncryptionService,
            api = api,
            currentSyncState = CurrentSyncState(MutableStateFlow(SyncState.RUNNING)),
            clock = testScope.testClock,
            userInfo = UserInfo(ownUserId, ownDeviceId, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        ) {
        override suspend fun generateEphemeralMediaKey(): ByteArray {
            val value = (mediaKeyCounter++ % 256).toByte()
            return ByteArray(32) { value }
        }
    }

    private lateinit var cut: TestPerMemberRtcSlotMembershipEncryptionHandler

    init {
        olmEncryptionService.returnEncryptOlm =
            Result.success(OlmEncryptedToDeviceEventContent(emptyMap(), Curve25519KeyValue("ownSenderKey")))
        apiConfig.endpoints { matrixJsonEndpoint(SendToDevice("m.room.encrypted", "*")) { sentToDevice = it.messages } }
    }

    @BeforeTest
    fun setup() {
        mediaKeyCounter = 0
        sentToDevice = null
        cut = TestPerMemberRtcSlotMembershipEncryptionHandler()
    }

    @Test
    fun `supports - per-member encryption - true`() = runTest {
        cut.supports(PerMemberRtcEncryption, null) shouldBe true
    }

    @Test
    fun `supports - non per-member encryption - false`() = runTest {
        cut.supports(RtcEncryption.Unknown(type = "unknown", raw = JsonObject(emptyMap())), null) shouldBe false
    }

    @Test
    fun `getEncryptionInfo - emits initial own key with index 0 and empty received keys`() = runTest {
        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(100.milliseconds)
        result.value.receivedKeys shouldBe emptyMap()
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        result.value.ownActiveKey.mediaKey?.key shouldBe ByteArray(32) { 0 }.encodeUnpaddedBase64()
        result.value.ownActiveKey.roomId shouldBe roomId
        result.value.ownActiveKey.memberId shouldBe ownMemberId
    }

    @Test
    fun `getEncryptionInfo - distributes own key to joining members`() = runTest {
        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(100.milliseconds)
        memberEvents.value = mapOf((alice to aliceMemberId) to joinEvent(aliceEventId))
        delay(500.milliseconds)

        val encryptedContent = olmEncryptionService.encryptOlmCalled?.first as? RtcEncryptionKeyEventContent
        encryptedContent.shouldNotBeNull()
        encryptedContent shouldBe result.value.ownActiveKey
        encryptedContent.roomId shouldBe roomId
        encryptedContent.memberId shouldBe ownMemberId
        encryptedContent.member shouldBe RtcEncryptionKeyEventContent.Member(ownMemberId, ownDeviceId)
        encryptedContent.mediaKey?.index shouldBe 0
        encryptedContent.mediaKey?.key shouldBe ByteArray(32) { 0 }.encodeUnpaddedBase64()
        result.value.ownActiveKey.mediaKey?.index shouldBe 0

        sentToDevice.shouldNotBeNull()[alice]?.get(aliceDevice).shouldNotBeNull()
    }

    @Test
    fun `getEncryptionInfo - periodically invalidates key when no membership changes`() = runTest {
        memberEvents.value = mapOf((alice to aliceMemberId) to joinEvent(aliceEventId))
        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(100.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        delay(config.periodicKeyRotation - 1.seconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        delay(2.seconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        delay(config.useKeyDelay)
        result.value.ownActiveKey.mediaKey?.index shouldBe 1
        result.value.ownActiveKey.mediaKey?.key shouldBe ByteArray(32) { 1 }.encodeUnpaddedBase64()

        val encryptedContent = olmEncryptionService.encryptOlmCalled?.first as? RtcEncryptionKeyEventContent
        encryptedContent.shouldNotBeNull()
        encryptedContent.roomId shouldBe roomId
        encryptedContent.memberId shouldBe ownMemberId
        encryptedContent.member shouldBe RtcEncryptionKeyEventContent.Member(ownMemberId, ownDeviceId)
        encryptedContent.mediaKey?.index shouldBe 1
        encryptedContent.mediaKey?.key shouldBe ByteArray(32) { 1 }.encodeUnpaddedBase64()

        sentToDevice.shouldNotBeNull()[alice]?.get(aliceDevice).shouldNotBeNull()
    }

    @Test
    fun `getEncryptionInfo - exposes received keys from olm events`() = runTest {
        memberEvents.value = mapOf((alice to aliceMemberId) to joinEvent(aliceEventId))
        val incomingKey =
            RtcEncryptionKeyEventContent(
                roomId = roomId,
                memberId = aliceMemberId,
                mediaKey = RtcEncryptionKeyEventContent.MediaKey("key_1", 0),
            )

        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(500.milliseconds)
        result.value.receivedKeys[alice to aliceMemberId] shouldBe emptyList()
        olmEventHandler.eventSubscribers.value.forEach {
            it(
                DecryptedOlmEventContainer(
                    encrypted =
                        ClientEvent.ToDeviceEvent(OlmEncryptedToDeviceEventContent(emptyMap(), aliceSenderKey), alice),
                    decrypted =
                        PlaintextOlmEvent(
                            content = incomingKey,
                            sender = alice,
                            senderKeys = keysOf(),
                            recipient = ownUserId,
                            recipientKeys = keysOf(),
                        ),
                )
            )
        }
        delay(500.milliseconds)
        result.value.receivedKeys[alice to aliceMemberId] shouldBe listOf(incomingKey)
    }

    @Test
    fun `getEncryptionInfo - buffers only last N received keys`() = runTest {
        memberEvents.value = mapOf((alice to aliceMemberId) to joinEvent(aliceEventId))

        suspend fun sendKey(index: Long) {
            val key =
                RtcEncryptionKeyEventContent(
                    roomId = roomId,
                    memberId = aliceMemberId,
                    mediaKey = RtcEncryptionKeyEventContent.MediaKey("key_$index", index),
                )
            olmEventHandler.eventSubscribers.value.forEach {
                it(
                    DecryptedOlmEventContainer(
                        encrypted =
                            ClientEvent.ToDeviceEvent(
                                OlmEncryptedToDeviceEventContent(emptyMap(), aliceSenderKey),
                                alice,
                            ),
                        decrypted =
                            PlaintextOlmEvent(
                                content = key,
                                sender = alice,
                                senderKeys = keysOf(),
                                recipient = ownUserId,
                                recipientKeys = keysOf(),
                            ),
                    )
                )
            }
        }

        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(500.milliseconds)
        sendKey(0)
        delay(100.milliseconds)
        sendKey(1)
        delay(100.milliseconds)
        sendKey(2)
        delay(500.milliseconds)

        result.value.receivedKeys[alice to aliceMemberId].shouldNotBeNull().map { it.mediaKey?.index } shouldBe
            listOf(1, 2)
    }

    @Test
    fun `getEncryptionInfo - rotates key after member leave and useKeyDelay`() = runTest {
        memberEvents.value = mapOf((alice to aliceMemberId) to joinEvent(aliceEventId))

        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(100.milliseconds)
        memberEvents.value = mapOf((alice to aliceMemberId) to leaveEvent(aliceLeaveEventId))
        delay(config.useKeyDelay)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        delay(1.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 1
    }

    @Test
    fun `getEncryptionInfo - does not rotate key on leave of member that never received the key`() = runTest {
        memberEvents.value = mapOf((alice to aliceMemberId) to leaveEvent(aliceLeaveEventId))
        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(100.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        delay(config.useKeyDelay + 1.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        olmEncryptionService.encryptOlmCalled shouldBe null
        sentToDevice shouldBe null
    }

    @Test
    fun `getEncryptionInfo - distributes next key before rotation after member leave`() = runTest {
        memberEvents.value =
            mapOf(
                (alice to aliceMemberId) to joinEvent(aliceEventId),
                (bob to bobMemberId) to joinEvent(bobEventId, bob, bobMemberId),
            )
        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(500.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        (olmEncryptionService.encryptOlmCalled?.first as? RtcEncryptionKeyEventContent)?.mediaKey?.index shouldBe 0

        memberEvents.value =
            mapOf(
                (alice to aliceMemberId) to leaveEvent(aliceLeaveEventId),
                (bob to bobMemberId) to joinEvent(bobEventId, bob, bobMemberId),
            )
        delay(config.useKeyDelay)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        val distributedNext = olmEncryptionService.encryptOlmCalled?.first as? RtcEncryptionKeyEventContent
        distributedNext.shouldNotBeNull()
        distributedNext.mediaKey?.index shouldBe 1
        distributedNext.mediaKey?.key shouldBe ByteArray(32) { 1 }.encodeUnpaddedBase64()
        sentToDevice.shouldNotBeNull()[bob]?.get(bobDevice).shouldNotBeNull()
        delay(1.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 1
        result.value.ownActiveKey.mediaKey?.key shouldBe ByteArray(32) { 1 }.encodeUnpaddedBase64()
    }

    @Test
    fun `getEncryptionInfo - rotates key on new join after grace period`() = runTest {
        memberEvents.value = mapOf((alice to aliceMemberId) to joinEvent(aliceEventId))
        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(100.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        delay(config.keyRotationGracePeriod)
        memberEvents.value =
            mapOf(
                (alice to aliceMemberId) to joinEvent(aliceEventId),
                (bob to bobMemberId) to joinEvent(bobEventId, bob, bobMemberId),
            )
        delay(config.useKeyDelay)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
        delay(1.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 1
        result.value.ownActiveKey.mediaKey?.key shouldBe ByteArray(32) { 1 }.encodeUnpaddedBase64()
    }

    @Test
    fun `getEncryptionInfo - do not rotates key on new join before grace period`() = runTest {
        memberEvents.value = mapOf((alice to aliceMemberId) to joinEvent(aliceEventId))
        val result =
            cut.getEncryptionInfo(
                    roomId = roomId,
                    slotId = slotId,
                    memberId = ownMemberId,
                    encryption = PerMemberRtcEncryption,
                    transports = null,
                    config = mapOf(PerMemberRtcEncryption to config),
                    memberEvents = memberEvents,
                )
                .filterIsInstance<PerMemberRtcSlotMembershipEncryptionInfo>()
                .stateIn(backgroundScope)
        delay(config.keyRotationGracePeriod - 10.milliseconds)
        memberEvents.value =
            mapOf(
                (alice to aliceMemberId) to joinEvent(aliceEventId),
                (bob to bobMemberId) to joinEvent(bobEventId, bob, bobMemberId),
            )
        delay(config.useKeyDelay + 1.milliseconds)
        result.value.ownActiveKey.mediaKey?.index shouldBe 0
    }

    private fun deviceKeys(userId: UserId, deviceId: String, senderKey: Curve25519KeyValue) =
        StoredDeviceKeys(
            SignedDeviceKeys(
                DeviceKeys(
                    userId = userId,
                    deviceId = deviceId,
                    algorithms = setOf(EncryptionAlgorithm.Megolm),
                    keys =
                        keysOf(Key.Curve25519Key(deviceId, senderKey), Key.Ed25519Key(deviceId, Ed25519KeyValue("ed"))),
                ),
                mapOf(),
            ),
            KeySignatureTrustLevel.Valid(true),
        )

    private fun joinEvent(
        eventId: EventId,
        sender: UserId = alice,
        memberId: RtcMemberId = aliceMemberId,
        originTimestamp: Long = 1234,
    ): ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent> =
        ClientEvent.RoomEvent.MessageEvent(
            RtcMemberEventContent.Join(
                slotId = slotId,
                slotEventId = EventId("\$slot"),
                member = RtcMemberEventContent.Member(memberId),
                application = CallRtcApplication.Member(),
                transports = null,
                stickyKey = memberId.value,
            ),
            eventId,
            sender,
            roomId,
            originTimestamp,
        )

    private fun leaveEvent(eventId: EventId): ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent> =
        ClientEvent.RoomEvent.MessageEvent(
            RtcMemberEventContent.Leave(
                slotId = slotId,
                slotEventId = EventId("\$slot"),
                member = RtcMemberEventContent.Member(aliceMemberId),
                reason = null,
                stickyKey = aliceMemberId.value,
            ),
            eventId,
            alice,
            roomId,
            1234,
        )
}
