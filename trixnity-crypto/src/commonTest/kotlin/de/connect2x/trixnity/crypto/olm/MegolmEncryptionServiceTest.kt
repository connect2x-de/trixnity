package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.DecryptedMegolmEvent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key.Curve25519Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import de.connect2x.trixnity.crypto.invoke
import de.connect2x.trixnity.crypto.mocks.MegolmEncryptionServiceRequestHandlerMock
import de.connect2x.trixnity.crypto.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.crypto.mocks.OlmStoreMock
import de.connect2x.trixnity.crypto.mocks.SignServiceMock
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService.DecryptMegolmError
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import de.connect2x.trixnity.crypto.sign.VerifyResult
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class MegolmEncryptionServiceTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val account = driver.olm.account
    private val message = driver.olm.message
    private val groupSession = driver.megolm.groupSession
    private val inboundGroupSession = driver.megolm.inboundGroupSession
    private val sessionKey = driver.megolm.sessionKey
    private val megolmMessage = driver.megolm.message

    private val json = createMatrixEventJson()
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDeviceId = "ALICEDEVICE"
    private val bobDeviceId = "BOBDEVICE"

    private val aliceAccount = account()
    private val bobAccount = account()

    private val aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.curve25519Key.base64)
    private val aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.ed25519Key.base64)
    private val aliceDeviceKeys = SignedDeviceKeys(
        DeviceKeys(
            alice,
            aliceDeviceId,
            setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
            Keys(keysOf(aliceCurveKey, aliceEdKey))
        ),
    )
    private val bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.curve25519Key.base64)
    private val bobEdKey = Ed25519Key(bobDeviceId, bobAccount.ed25519Key.base64)

    private val mockSignService = SignServiceMock()
    private val megolmEncryptionServiceRequestHandlerMock = MegolmEncryptionServiceRequestHandlerMock()
    private val olmStoreMock = OlmStoreMock()
    private val olmEncryptionService = OlmEncryptionServiceMock()

    @OptIn(ExperimentalSerializationApi::class)
    private val decryptedMegolmEventSerializer =
        requireNotNull(json.serializersModule.getContextual(DecryptedMegolmEvent::class))

    private val relatesTo = RelatesTo.Replace(EventId("$1fancyEvent"), RoomMessageEventContent.TextBased.Text("Hi"))
    private val decryptedMegolmEventContent = RoomMessageEventContent.TextBased.Text("*Hi", relatesTo = relatesTo)
    private val room = RoomId("!room:server")
    private val decryptedMegolmEvent = DecryptedMegolmEvent(decryptedMegolmEventContent, room)

    @BeforeTest
    fun beforeTest() {
        olmStoreMock.devices[bob] = mapOf(
            bobDeviceId to Signed(
                DeviceKeys(
                    userId = bob,
                    deviceId = bobDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(bobCurveKey, bobEdKey))
                )
            )
        )
        olmStoreMock.devices[alice] = mapOf(
            aliceDeviceId to Signed(
                DeviceKeys(
                    userId = alice,
                    deviceId = aliceDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(aliceCurveKey, aliceEdKey))
                )
            )
        )
        olmStoreMock.roomMembers[room] = setOf(alice, bob)
        olmStoreMock.olmAccount.value = aliceAccount.pickle()
        mockSignService.returnVerify = VerifyResult.Valid
        mockSignService.selfSignedDeviceKeys = aliceDeviceKeys
        olmEncryptionService.encryptOlm.clear()
    }

    private val cut = MegolmEncryptionServiceImpl(
        UserInfo(alice, aliceDeviceId, aliceEdKey, aliceCurveKey),
        json,
        olmStoreMock,
        megolmEncryptionServiceRequestHandlerMock,
        olmEncryptionService,
        testScope.testClock,
        driver,
    )

    suspend fun shouldEncryptMessage(
        settings: EncryptionEventContent,
        expectedMessageCount: Int,
    ) {
        val olmCipher = OlmEncryptedToDeviceEventContent(ciphertext = mapOf(), senderKey = aliceCurveKey.value)
        olmEncryptionService.encryptOlm[bob to bobDeviceId] = Result.success(olmCipher)

        val result = cut.encryptMegolm(decryptedMegolmEventContent, room, settings).getOrThrow()

        val storedOutboundSession = olmStoreMock.outboundMegolmSession[room]
        assertNotNull(storedOutboundSession)
        assertSoftly(storedOutboundSession) {
            encryptedMessageCount shouldBe expectedMessageCount
            room shouldBe room
        }

        val outboundSession = groupSession.fromPickle(storedOutboundSession.pickled)

        assertSoftly(result) {
            @Suppress("DEPRECATION")
            this.senderKey shouldBe aliceCurveKey.value
            @Suppress("DEPRECATION")
            this.deviceId shouldBe aliceDeviceId
            this.sessionId shouldBe outboundSession.sessionId
            this.relatesTo shouldBe this@MegolmEncryptionServiceTest.relatesTo.copy(newContent = null)
        }

        val sendToDeviceEvents = megolmEncryptionServiceRequestHandlerMock.sendToDeviceParams
        sendToDeviceEvents.firstOrNull()?.get(bob)?.get(bobDeviceId) shouldBe olmCipher

        val sessionId = outboundSession.sessionId
        val storedInboundSession = olmStoreMock.inboundMegolmSession[sessionId to room]
        assertNotNull(storedInboundSession)
        assertSoftly(storedInboundSession) {
            sessionId shouldBe sessionId
            senderKey shouldBe aliceCurveKey.value
            room shouldBe room
        }

        val inboundSession = inboundGroupSession.fromPickle(storedInboundSession.pickled)

        json.decodeFromString(
            decryptedMegolmEventSerializer, inboundSession.decrypt(megolmMessage(result.ciphertext)).plaintext
        ) shouldBe decryptedMegolmEvent
    }

    @Test
    fun `encryptMegolm - encrypt without stored megolm session`() = runTest {
        shouldEncryptMessage(EncryptionEventContent(), 1)
    }

    @Test
    fun `encryptMegolm - not send keys to own dehydrated device`() = runTest {
        val aliceDeviceIdDehydrated = aliceDeviceId + "dehydrated"
        olmStoreMock.devices[alice] = mapOf(
            aliceDeviceId to Signed(
                DeviceKeys(
                    userId = alice,
                    deviceId = aliceDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(aliceCurveKey, aliceEdKey)),
                )
            ),
            aliceDeviceIdDehydrated to Signed(
                DeviceKeys(
                    userId = alice,
                    deviceId = aliceDeviceIdDehydrated,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(aliceCurveKey, aliceEdKey)),
                    dehydrated = true
                )
            )
        )
        shouldEncryptMessage(EncryptionEventContent(), 1)
        megolmEncryptionServiceRequestHandlerMock.sendToDeviceParams.firstOrNull().shouldNotBeNull()[alice]
            .shouldBeNull()
    }

    @Test
    fun `encryptMegolm - not update session when not possible to send key to a recipient`() =
        runTest {
            val olmCipher = OlmEncryptedToDeviceEventContent(ciphertext = mapOf(), senderKey = aliceCurveKey.value)
            olmEncryptionService.encryptOlm[bob to bobDeviceId] = Result.success(olmCipher)
            megolmEncryptionServiceRequestHandlerMock.sendToDevice = Result.failure(
                IllegalStateException("random network error")
            )

            cut.encryptMegolm(decryptedMegolmEventContent, room, EncryptionEventContent())
                .exceptionOrNull().shouldBeInstanceOf<MegolmEncryptionService.EncryptMegolmError.NetworkError>()
            olmStoreMock.outboundMegolmSession.shouldBeEmpty()
            olmStoreMock.inboundMegolmSession.shouldBeEmpty()
        }

    @Test
    fun `encryptMegolm - not update session when not possible to encrypt to a recipient because of network issue`() =
        runTest {
            olmEncryptionService.encryptOlm[bob to bobDeviceId] =
                Result.failure(EncryptOlmError.NetworkError(IllegalStateException("random network error")))

            cut.encryptMegolm(decryptedMegolmEventContent, room, EncryptionEventContent())
                .exceptionOrNull().shouldBeInstanceOf<MegolmEncryptionService.EncryptMegolmError.NetworkError>()
            olmStoreMock.outboundMegolmSession.shouldBeEmpty()
            olmStoreMock.inboundMegolmSession.shouldBeEmpty()
        }

    private fun createExistingOutboundSession() {
        val outboundSession = groupSession()
        repeat(23) { outboundSession.encrypt("bla") }

        olmStoreMock.outboundMegolmSession[room] = StoredOutboundMegolmSession(
            roomId = room,
            createdAt = testScope.testClock.now(),
            encryptedMessageCount = 23,
            newDevices = mapOf(bob to setOf(bobDeviceId)),
            pickled = outboundSession.pickle()
        )

        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )

        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = aliceCurveKey.value,
            senderSigningKey = aliceEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
    }

    @Test
    fun `encryptMegolm - send megolm sessions to new devices and encrypt`() = runTest {
        createExistingOutboundSession()
        shouldEncryptMessage(EncryptionEventContent(), 24)
    }

    @Test
    fun `encryptMegolm - crete new megolm session when rotation period passed`() = runTest {
        val previousSession =
            StoredOutboundMegolmSession(
                roomId = room,
                createdAt = testClock.now() - 24.milliseconds,
                encryptedMessageCount = 5,
                newDevices = emptyMap(),
                pickled = "is irrelevant"
            )
        olmStoreMock.outboundMegolmSession[room] = previousSession
        shouldEncryptMessage(EncryptionEventContent(rotationPeriodMs = 24), 1)
        olmStoreMock.outboundMegolmSession[room] shouldNotBe previousSession
    }

    @Test
    fun `encryptMegolm - create new megolm session when message count passed`() = runTest {
        val previousSession = StoredOutboundMegolmSession(
            roomId = room,
            createdAt = testClock.now(),
            encryptedMessageCount = 24,
            newDevices = emptyMap(),
            pickled = "is irrelevant"
        )
        olmStoreMock.outboundMegolmSession[room] = previousSession
        shouldEncryptMessage(EncryptionEventContent(rotationPeriodMsgs = 24), 1)
        olmStoreMock.outboundMegolmSession[room] shouldNotBe previousSession
    }

    @Test
    fun `decryptMegolm - decrypt megolm event 1`() = runTest {
        val outboundSession = groupSession()
        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        val ciphertext =
            outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext),
                    bobCurveKey.value,
                    bobDeviceId,
                    outboundSession.sessionId,
                    relatesTo = relatesTo
                ), EventId("\$event"), bob, room, 1234
            )
        )
            .getOrThrow() shouldBe decryptedMegolmEvent.copy(content = decryptedMegolmEvent.content.copy(relatesTo = relatesTo))

        olmStoreMock.inboundMegolmSessionIndex[Triple(
            outboundSession.sessionId, room, 0
        )] shouldBe StoredInboundMegolmMessageIndex(
            outboundSession.sessionId, room, 0, EventId("\$event"), 1234
        )
    }

    @Test
    fun `decryptMegolm - decrypt megolm event 2`() = runTest {
        val outboundSession = groupSession()
        val ciphertext = // encrypted before session saved
            outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))

        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext),
                    bobCurveKey.value,
                    bobDeviceId,
                    outboundSession.sessionId,
                    relatesTo = relatesTo
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.MegolmKeyUnknownMessageIndex>()
    }

    @Test
    fun `decryptMegolm - fail when no keys were send to us`() = runTest {
        val session = groupSession()
        val ciphertext = session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, session.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.MegolmKeyNotFound>()
    }

    @Test
    fun `decryptMegolm - handle manipulated roomId in megolmEvent`() = runTest {
        val outboundSession = groupSession()
        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        val ciphertext = outboundSession.encrypt(
            json.encodeToString(
                decryptedMegolmEventSerializer, decryptedMegolmEvent.copy(roomId = RoomId("!other:server"))
            )
        )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, outboundSession.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
    }

    @Test
    fun `decryptMegolm - handle manipulated message index`() = runTest {
        val outboundSession = groupSession()
        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        val ciphertext =
            outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
        olmStoreMock.inboundMegolmSessionIndex[Triple(outboundSession.sessionId, room, 0)] =
            StoredInboundMegolmMessageIndex(
                outboundSession.sessionId, room, 0, EventId("\$otherEvent"), 1234
            )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, outboundSession.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
        olmStoreMock.inboundMegolmSessionIndex[Triple(outboundSession.sessionId, room, 0)]
        StoredInboundMegolmMessageIndex(
            outboundSession.sessionId, room, 0, EventId("\$event"), 4321
        )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, outboundSession.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
    }
}
