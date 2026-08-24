package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.clientserverapi.model.key.ClaimKeys
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.PlaintextOlmEvent
import de.connect2x.trixnity.core.model.events.m.RoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType.INITIAL_PRE_KEY
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType.ORDINARY
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Key.Curve25519Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.OlmMessageValue
import de.connect2x.trixnity.core.model.keys.SessionKeyValue
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.crypto.driver.olm.Account
import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import de.connect2x.trixnity.crypto.invoke
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.mocks.OlmEncryptionServiceRequestHandlerMock
import de.connect2x.trixnity.crypto.mocks.OlmStoreMock
import de.connect2x.trixnity.crypto.mocks.SignServiceMock
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.DecryptOlmError
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.OlmRecovery
import de.connect2x.trixnity.crypto.sign.VerifyResult
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant.Companion.fromEpochMilliseconds
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.ExperimentalSerializationApi

class OlmEncryptionServiceTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val account = driver.olm.account
    private val message = driver.olm.message
    private val json = createMatrixEventJson()
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDeviceId = "ALICEDEVICE"
    private val bobDeviceId = "BOBDEVICE"

    private val aliceAccount = account()
    private val bobAccount = account()

    private val aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.curve25519Key.base64)
    private val aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.ed25519Key.base64)
    private val aliceDeviceKeys =
        SignedDeviceKeys(
            DeviceKeys(
                alice,
                aliceDeviceId,
                setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                Keys(keysOf(aliceCurveKey, aliceEdKey)),
            )
        )
    private val bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.curve25519Key.base64)
    private val bobEdKey = Ed25519Key(bobDeviceId, bobAccount.ed25519Key.base64)
    private val bobDeviceKeys =
        SignedDeviceKeys(
            DeviceKeys(
                bob,
                bobDeviceId,
                setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                Keys(keysOf(bobCurveKey, bobEdKey)),
            )
        )

    private val mockSignService = SignServiceMock()
    private val olmEncryptionServiceRequestHandlerMock = OlmEncryptionServiceRequestHandlerMock()
    private val olmStoreMock = OlmStoreMock()

    @OptIn(ExperimentalSerializationApi::class)
    private val plaintextOlmEventSerializer =
        requireNotNull(json.serializersModule.getContextual(PlaintextOlmEvent::class))
    private val decryptedOlmEventContent =
        RoomKeyEventContent(
            RoomId("!room:server"),
            "sessionId",
            SessionKeyValue("sessionKey"),
            EncryptionAlgorithm.Megolm,
        )

    private val room = RoomId("!room:server")

    @BeforeTest
    fun beforeTest() {
        olmStoreMock.devices[bob] =
            mapOf(
                bobDeviceId to
                    Signed(
                        DeviceKeys(
                            userId = bob,
                            deviceId = bobDeviceId,
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                        )
                    )
            )
        olmStoreMock.devices[alice] =
            mapOf(
                aliceDeviceId to
                    Signed(
                        DeviceKeys(
                            userId = alice,
                            deviceId = aliceDeviceId,
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = Keys(keysOf(aliceCurveKey, aliceEdKey)),
                        )
                    )
            )
        olmStoreMock.olmSessions.clear()
        olmStoreMock.roomMembers[room] = setOf(alice, bob)
        olmStoreMock.olmAccount.value = aliceAccount.pickle()
        mockSignService.returnVerify = VerifyResult.Valid
        mockSignService.selfSignedDeviceKeys = aliceDeviceKeys
    }

    private val sendPlaintextOlmEvent =
        PlaintextOlmEvent(
            content = decryptedOlmEventContent,
            sender = alice,
            senderKeys = keysOf(aliceEdKey.copy(id = null)),
            senderDeviceKeys = aliceDeviceKeys,
            recipient = bob,
            recipientKeys = keysOf(bobEdKey.copy(id = null)),
        )
    private val receivePlaintextOlmEvent =
        PlaintextOlmEvent(
            content = decryptedOlmEventContent,
            sender = bob,
            senderKeys = keysOf(bobEdKey.copy(id = null)),
            senderDeviceKeys = bobDeviceKeys,
            recipient = alice,
            recipientKeys = keysOf(aliceEdKey.copy(id = null)),
        )

    private val cut =
        OlmEncryptionServiceImpl(
            UserInfo(alice, aliceDeviceId, aliceEdKey, aliceCurveKey),
            json,
            olmStoreMock,
            olmEncryptionServiceRequestHandlerMock,
            mockSignService,
            testScope.testClock,
            driver,
        )

    private fun Account.getOneTimeKey(store: Boolean = false): Curve25519PublicKey {
        generateOneTimeKeys(1)
        return oneTimeKeys.values.first().also {
            markKeysAsPublished()
            if (store) olmStoreMock.olmAccount.value = pickle()
        }
    }

    private fun mockClaimKeys() {
        val bobsFakeSignedCurveKey =
            Key.SignedCurve25519Key(bobDeviceId, bobAccount.getOneTimeKey().base64, signatures = mapOf())
        olmEncryptionServiceRequestHandlerMock.claimKeys =
            Result.success(
                ClaimKeys.Response(emptyMap(), mapOf(bob to mapOf(bobDeviceId to keysOf(bobsFakeSignedCurveKey))))
            )
    }

    private suspend fun shouldEncryptOlm() {
        val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
        val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value.value]
        assertNotNull(encryptedCipherText)

        encryptedMessage.senderKey shouldBe aliceCurveKey.value
        encryptedCipherText.type shouldBe INITIAL_PRE_KEY

        val (plaintext, _) =
            bobAccount.createInboundSession(
                theirIdentityKey = aliceAccount.curve25519Key,
                preKeyMessage = message.preKey(encryptedCipherText.body),
            )

        json.decodeFromString(plaintextOlmEventSerializer, plaintext) shouldBe sendPlaintextOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value] shouldNotBe null
    }

    @Test
    fun `encryptOlm - encrypt without stored olm encrypt session`() = runTest {
        mockClaimKeys()
        shouldEncryptOlm()
    }

    @Test
    fun `encryptOlm - encrypt multiple without stored olm encrypt session`() = runTest {
        val bobsFakeSignedCurveKey =
            Key.SignedCurve25519Key(bobDeviceId, bobAccount.getOneTimeKey().base64, signatures = mapOf())
        olmEncryptionServiceRequestHandlerMock.claimKeys =
            Result.success(
                ClaimKeys.Response(emptyMap(), mapOf(bob to mapOf(bobDeviceId to keysOf(bobsFakeSignedCurveKey))))
            )

        val results =
            cut.encryptOlm(decryptedOlmEventContent, setOf(bob to bobDeviceId, UserId("unknown", "server") to "d"))
        val encryptedMessage = results.get(bob to bobDeviceId)?.getOrThrow()
        val encryptedCipherText = encryptedMessage?.ciphertext[bobCurveKey.value.value]
        assertNotNull(encryptedCipherText)

        encryptedMessage.senderKey shouldBe aliceCurveKey.value
        encryptedCipherText.type shouldBe INITIAL_PRE_KEY

        val (plaintext, _) =
            bobAccount.createInboundSession(
                theirIdentityKey = aliceAccount.curve25519Key,
                preKeyMessage = message.preKey(encryptedCipherText.body),
            )

        json.decodeFromString(plaintextOlmEventSerializer, plaintext) shouldBe sendPlaintextOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value] shouldNotBe null

        results.get(UserId("unknown", "server") to "d")?.exceptionOrNull() shouldBe
            EncryptOlmError.NoOlmSupported("device keys not found")
    }

    @Test
    fun `encryptOlm - encrypt for verified dehydrated device`() = runTest {
        mockClaimKeys()
        olmStoreMock.devices[bob] =
            mapOf(
                bobDeviceId to
                    Signed(
                        DeviceKeys(
                            userId = bob,
                            deviceId = bobDeviceId,
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                            dehydrated = true,
                        )
                    )
            )
        olmStoreMock.deviceTrustLevels[bob] = mapOf(bobDeviceId to DeviceTrustLevel.CrossSigned(false))
        shouldEncryptOlm()
    }

    @Test
    fun `encryptOlm - is Failure when try to encrypt with unverified dehydrated device`() = runTest {
        olmStoreMock.devices[bob] =
            mapOf(
                bobDeviceId to
                    Signed(
                        DeviceKeys(
                            userId = bob,
                            deviceId = bobDeviceId,
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                            dehydrated = true,
                        )
                    )
            )
        olmStoreMock.deviceTrustLevels[bob] = mapOf(bobDeviceId to DeviceTrustLevel.NotCrossSigned)
        val result = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId)
        result.exceptionOrNull().shouldBeInstanceOf<EncryptOlmError.DehydratedDeviceNotCrossSigned>()
    }

    @Test
    fun `encryptOlm - is failure when one time key is invalid without stored olm encrypt session`() = runTest {
        mockClaimKeys()
        mockSignService.returnVerify = VerifyResult.Invalid("dino")

        cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).exceptionOrNull() shouldBe
            EncryptOlmError.NoOlmSupported("one time key validation failed: Invalid(reason=dino)")
        olmStoreMock.olmSessions.shouldBeEmpty()
    }

    @Test
    fun `encryptOlm - encrypt event with stored session`() = runTest {
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )

        val (_, aliceSession) =
            aliceAccount.createInboundSession(
                theirIdentityKey = bobAccount.curve25519Key,
                preKeyMessage = bobSession.encrypt("first message") as Message.PreKey,
            )

        val storedOlmSession =
            StoredOlmSession(
                senderKey = bobCurveKey.value,
                sessionId = aliceSession.sessionId,
                lastUsedAt = testClock.now(),
                createdAt = testClock.now(),
                pickled = aliceSession.pickle(),
                initiatedByThisDevice = false,
            )

        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
        val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value.value]
        assertNotNull(encryptedCipherText)

        encryptedMessage.senderKey shouldBe aliceCurveKey.value
        encryptedCipherText.type shouldBe ORDINARY

        json.decodeFromString(plaintextOlmEventSerializer, bobSession.decrypt(message(encryptedCipherText))) shouldBe
            sendPlaintextOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
    }

    @Test
    fun `recoverOlm - not create multiple recovery sessions in short time`() = runTest {
        mockClaimKeys()
        val aliceSession =
            aliceAccount.createOutboundSession(
                identityKey = bobAccount.curve25519Key,
                oneTimeKey = bobAccount.getOneTimeKey(),
            )

        aliceSession.encrypt("first message")

        val storedOlmSession =
            StoredOlmSession(
                senderKey = bobCurveKey.value,
                sessionId = aliceSession.sessionId,
                lastUsedAt = testClock.now() - 3.seconds,
                createdAt = testClock.now(),
                pickled = aliceSession.pickle(),
                initiatedByThisDevice = false,
            )
        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        cut.recoverOlm(OlmRecovery(bob, bobDeviceId, testClock.now() - 2.seconds)).getOrThrow().shouldNotBeNull()
        cut.recoverOlm(OlmRecovery(bob, bobDeviceId, testClock.now() - 1.seconds)).getOrThrow().shouldBeNull()
    }

    @Test
    fun `decryptOlm - decrypt pre key message from new session`() = runTest {
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )

        val encryptedMessage =
            bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .getOrThrow() shouldBe receivePlaintextOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldHaveSize 1

        val account = account.fromPickle(pickle = olmStoreMock.olmAccount.value.shouldNotBeNull())

        // we check, that the one time key cannot be used twice
        shouldThrow<CryptoDriverException> {
            account.createInboundSession(
                theirIdentityKey = bobAccount.curve25519Key,
                preKeyMessage = encryptedMessage as Message.PreKey,
            )
        }
    }

    @Test
    fun `decryptOlm - ignore dehydrated device`() = runTest {
        olmStoreMock.devices[bob] =
            mapOf(
                bobDeviceId to
                    Signed(
                        DeviceKeys(
                            userId = bob,
                            deviceId = bobDeviceId,
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                            dehydrated = true,
                        )
                    )
            )

        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )

        val encryptedMessage =
            bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .exceptionOrNull() shouldBe DecryptOlmError.DehydratedDeviceNotAllowed()

        olmStoreMock.olmSessions[bobCurveKey.value].shouldBeNull()
    }

    @Test
    fun `decryptOlm - not decrypt pre key message when the 5 last created sessions are not older then 1 hour`() =
        runTest {
            val bobSession =
                bobAccount.createOutboundSession(
                    identityKey = aliceAccount.curve25519Key,
                    oneTimeKey = aliceAccount.getOneTimeKey(true),
                )
            val encryptedMessage =
                bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

            val existingSessions =
                (0..4)
                    .map { pseudoSessionId ->
                        val dummyAccount = account()

                        val aliceSession =
                            aliceAccount.createOutboundSession(
                                identityKey = dummyAccount.curve25519Key,
                                oneTimeKey = dummyAccount.getOneTimeKey(),
                            )

                        StoredOlmSession(
                            senderKey = bobCurveKey.value,
                            sessionId = pseudoSessionId.toString(),
                            lastUsedAt = testClock.now(),
                            createdAt = testClock.now(),
                            pickled = aliceSession.pickle(),
                            initiatedByThisDevice = false,
                        )
                    }
                    .toSet()

            olmStoreMock.olmSessions[bobCurveKey.value] = existingSessions
            cut.decryptOlm(
                    ClientEvent.ToDeviceEvent(
                        OlmEncryptedToDeviceEventContent(
                            ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                            senderKey = bobCurveKey.value,
                        ),
                        bob,
                    )
                )
                .exceptionOrNull()
                .shouldBeInstanceOf<DecryptOlmError.TooManySessions>()
        }

    @Test
    fun `decryptOlm - fail on ordinary message`() = runTest {
        mockClaimKeys()
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )
        val encryptedMessage =
            bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext =
                            mapOf(
                                aliceCurveKey.value.value to
                                    CiphertextInfo(OlmMessageValue.of(encryptedMessage), ORDINARY)
                            ),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .exceptionOrNull()
            .shouldBeInstanceOf<DecryptOlmError.NoMatchingOlmSessionFound>()
            .olmRecovery shouldBe OlmRecovery(bob, bobDeviceId, testClock.now())
    }

    @Test
    fun `decryptOlm - decrypt ordinary message`() = runTest {
        val aliceSession =
            aliceAccount.createOutboundSession(
                identityKey = bobAccount.curve25519Key,
                oneTimeKey = bobAccount.getOneTimeKey(),
            )

        val firstMessage = aliceSession.encrypt("first message") as Message.PreKey

        val (_, bobSession) = bobAccount.createInboundSession(preKeyMessage = firstMessage)

        val encryptedMessage =
            bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

        val storedOlmSession =
            StoredOlmSession(
                senderKey = bobCurveKey.value,
                sessionId = aliceSession.sessionId,
                lastUsedAt = testClock.now(),
                createdAt = testClock.now(),
                pickled = aliceSession.pickle(),
                initiatedByThisDevice = false,
            )

        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .getOrThrow() shouldBe receivePlaintextOlmEvent
        olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
    }

    @Test
    fun `decryptOlm - try multiple sessions descended by last used`() = runTest {
        val aliceSession1 =
            aliceAccount.createOutboundSession(
                identityKey = bobAccount.curve25519Key,
                oneTimeKey = bobAccount.getOneTimeKey(),
            )
        val aliceSession2 =
            aliceAccount.createOutboundSession(
                identityKey = bobAccount.curve25519Key,
                oneTimeKey = bobAccount.getOneTimeKey(),
            )
        val aliceSession3 =
            aliceAccount.createOutboundSession(
                identityKey = bobAccount.curve25519Key,
                oneTimeKey = bobAccount.getOneTimeKey(),
            )

        val firstMessage = aliceSession1.encrypt("first message") as Message.PreKey

        val (_, bobSession) = bobAccount.createInboundSession(preKeyMessage = firstMessage)

        val encryptedMessage =
            bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

        val storedOlmSession1 =
            StoredOlmSession(
                senderKey = bobCurveKey.value,
                sessionId = aliceSession1.sessionId,
                lastUsedAt = testClock.now(),
                createdAt = testClock.now(),
                pickled = aliceSession1.pickle(),
                initiatedByThisDevice = false,
            )
        val storedOlmSession2 =
            StoredOlmSession(
                senderKey = bobCurveKey.value,
                sessionId = aliceSession2.sessionId,
                lastUsedAt = fromEpochMilliseconds(24),
                createdAt = testClock.now(),
                pickled = aliceSession2.pickle(),
                initiatedByThisDevice = false,
            )
        val storedOlmSession3 =
            StoredOlmSession(
                senderKey = bobCurveKey.value,
                sessionId = aliceSession3.sessionId,
                lastUsedAt = testClock.now(),
                createdAt = testClock.now(),
                pickled = aliceSession3.pickle(),
                initiatedByThisDevice = false,
            )
        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession2, storedOlmSession1, storedOlmSession3)

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .getOrThrow() shouldBe receivePlaintextOlmEvent
        olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldNotContain storedOlmSession1
    }

    suspend fun TestScope.handleManipulation(manipulatedOlmEvent: PlaintextOlmEvent<RoomKeyEventContent>) {
        val aliceSession =
            aliceAccount.createOutboundSession(
                identityKey = bobAccount.curve25519Key,
                oneTimeKey = bobAccount.getOneTimeKey(),
            )
        val firstMessage = aliceSession.encrypt("first message") as Message.PreKey

        val storedOlmSession =
            StoredOlmSession(
                senderKey = bobCurveKey.value,
                sessionId = aliceSession.sessionId,
                lastUsedAt = testClock.now(),
                createdAt = testClock.now(),
                pickled = aliceSession.pickle(),
                initiatedByThisDevice = false,
            )
        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        val (_, bobSession) = bobAccount.createInboundSession(preKeyMessage = firstMessage)

        val encryptedMessage = bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, manipulatedOlmEvent))

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .exceptionOrNull()
            .shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
    }

    @Test
    fun `decryptOlm - handle manipulated sender`() = runTest {
        handleManipulation(receivePlaintextOlmEvent.copy(sender = UserId("cedric", "server")))
    }

    @Test
    fun `decryptOlm - handle manipulated senderKeys`() = runTest {
        handleManipulation(receivePlaintextOlmEvent.copy(senderKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key"))))
    }

    @Test
    fun `decryptOlm - handle manipulated recipient`() = runTest {
        handleManipulation(receivePlaintextOlmEvent.copy(recipient = UserId("cedric", "server")))
    }

    @Test
    fun `decryptOlm - handle manipulated recipientKeys`() = runTest {
        handleManipulation(
            receivePlaintextOlmEvent.copy(recipientKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key")))
        )
    }

    @Test
    fun `decryptOlm - decrypt message with sender device keys in decrypted event`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )
        val encryptedMessage =
            bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .getOrThrow() shouldBe receivePlaintextOlmEvent
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys userId did not match`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )
        val encryptedMessage =
            bobSession.encrypt(
                json.encodeToString(
                    plaintextOlmEventSerializer,
                    receivePlaintextOlmEvent.copy(
                        senderDeviceKeys =
                            Signed(
                                checkNotNull(receivePlaintextOlmEvent.senderDeviceKeys?.signed?.copy(userId = alice)),
                                null,
                            )
                    ),
                )
            )

        cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
            .exceptionOrNull()
            .shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys in decrypted event has invalid signature`() = runTest {
        olmStoreMock.devices.clear()
        mockSignService.returnVerify = VerifyResult.Invalid("invalid signature")
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )
        val encryptedMessage =
            bobSession.encrypt(json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent))

        val result =
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
        result.exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys are missing from store and event`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )
        val encryptedMessage =
            bobSession.encrypt(
                json.encodeToString(plaintextOlmEventSerializer, receivePlaintextOlmEvent.copy(senderDeviceKeys = null))
            )

        val result =
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
        result.exceptionOrNull() shouldBe DecryptOlmError.ValidationFailed("no sender device keys found")
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys are dehydrated`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession =
            bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key,
                oneTimeKey = aliceAccount.getOneTimeKey(true),
            )
        val encryptedMessage =
            bobSession.encrypt(
                json.encodeToString(
                    plaintextOlmEventSerializer,
                    receivePlaintextOlmEvent.copy(
                        senderDeviceKeys =
                            Signed(
                                checkNotNull(
                                    receivePlaintextOlmEvent.senderDeviceKeys?.signed?.copy(dehydrated = true)
                                ),
                                null,
                            )
                    ),
                )
            )

        val result =
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)),
                        senderKey = bobCurveKey.value,
                    ),
                    bob,
                )
            )
        result.exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.DehydratedDeviceNotAllowed>()
    }
}
