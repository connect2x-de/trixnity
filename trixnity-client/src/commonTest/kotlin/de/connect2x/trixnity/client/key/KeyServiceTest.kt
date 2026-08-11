package de.connect2x.trixnity.client.key

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryGlobalAccountDataStore
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.KeyTrustServiceMock
import de.connect2x.trixnity.client.mocks.RoomServiceMock
import de.connect2x.trixnity.client.mocks.SignServiceMock
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel.Valid
import de.connect2x.trixnity.client.store.StoredCrossSigningKeys
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.UIA
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeyBackupVersionByVersion
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeysBackupVersionResponse
import de.connect2x.trixnity.clientserverapi.model.key.SetCrossSigningKeys
import de.connect2x.trixnity.clientserverapi.model.key.SetRoomKeyBackupVersion
import de.connect2x.trixnity.clientserverapi.model.key.SetRoomKeyBackupVersionRequest
import de.connect2x.trixnity.clientserverapi.model.uia.ResponseWithUIA
import de.connect2x.trixnity.clientserverapi.model.user.SetGlobalAccountData
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.DehydratedDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import de.connect2x.trixnity.core.model.keys.CrossSigningKeys
import de.connect2x.trixnity.core.model.keys.CrossSigningKeysUsage
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.RoomKeyBackupAuthData.RoomKeyBackupV1AuthData
import de.connect2x.trixnity.core.model.keys.SignedCrossSigningKeys
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.crypto.SecretType.M_CROSS_SIGNING_SELF_SIGNING
import de.connect2x.trixnity.crypto.SecretType.M_CROSS_SIGNING_USER_SIGNING
import de.connect2x.trixnity.crypto.SecretType.M_DEHYDRATED_DEVICE
import de.connect2x.trixnity.crypto.SecretType.M_MEGOLM_BACKUP_V1
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

@OptIn(MSC3814::class)
class KeyServiceTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val alice = UserId("alice", "server")
    private val aliceDevice = "ALICEDEVICE"

    private val signServiceMock = SignServiceMock()
    private val roomServiceMock = RoomServiceMock()
    private val keyTrustServiceMock = KeyTrustServiceMock()

    private val keyStore = getInMemoryKeyStore { tm.writeTransaction { deleteAll() } }
    private val olmCryptoStore = getInMemoryOlmStore { tm.writeTransaction { deleteAll() } }
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore { tm.writeTransaction { deleteAll() } }

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = KeyServiceImpl(
        userInfo = UserInfo(alice, aliceDevice, Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        keyStore = keyStore,
        olmCryptoStore = olmCryptoStore,
        globalAccountDataStore = globalAccountDataStore,
        tm = tm,
        roomService = roomServiceMock,
        signService = signServiceMock,
        keyTrustService = keyTrustServiceMock,
        api = api,
        matrixClientConfiguration = MatrixClientConfiguration().apply { experimentalFeatures.enableMSC3814 = true },
        driver = driver,
    )

    private var secretKeyEventContentCalled = false
    private var capturedPassphrase: AesHmacSha2Key.SecretStorageKeyPassphrase? = null
    private var defaultSecretKeyEventContentCalled = false
    private var masterKeyEventContentCalled = false
    private var userSigningKeyEventContentCalled = false
    private var selfSigningKeyEventContentCalled = false
    private var keyBackupEventContentCalled = false
    private var setRoomKeyBackupVersionCalled = false
    private var dehydratedDeviceEventContentCalled = false
    private var setCrossSigningKeysCalled = false

    init {
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.secret_storage.key.*"),
            ) {
                it.shouldBeInstanceOf<AesHmacSha2Key>()
                it.iv shouldNot beEmpty()
                it.mac shouldNot beEmpty()
                capturedPassphrase = it.passphrase
                secretKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.secret_storage.default_key")
            ) {
                it.shouldBeInstanceOf<DefaultSecretKeyEventContent>()
                it.key.length shouldBeGreaterThan 10
                defaultSecretKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.cross_signing.master")
            ) {
                it.shouldBeInstanceOf<MasterKeyEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                masterKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.cross_signing.user_signing")
            ) {
                it.shouldBeInstanceOf<UserSigningKeyEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                userSigningKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.cross_signing.self_signing")
            ) {
                it.shouldBeInstanceOf<SelfSigningKeyEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                selfSigningKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "org.matrix.msc3814")
            ) {
                it.shouldBeInstanceOf<DehydratedDeviceEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                dehydratedDeviceEventContentCalled = true
            }
            matrixJsonEndpoint(SetGlobalAccountData(alice, "m.megolm_backup.v1")) {
                keyBackupEventContentCalled = true
            }
            apiConfig.endpoints {
                matrixJsonEndpoint(SetRoomKeyBackupVersion) {
                    setRoomKeyBackupVersionCalled = true
                    it.shouldBeInstanceOf<SetRoomKeyBackupVersionRequest.V1>()
                    it.authData.publicKey.value shouldNot beEmpty()
                    it.authData.signatures[alice]?.keys shouldBe setOf(
                        Ed25519Key(aliceDevice, "s1"),
                        Key.of(driver.key.ed25519SecretKey().use { it.publicKey })
                    )
                    it.version shouldBe null
                    SetRoomKeyBackupVersion.Response("1")
                }
                matrixJsonEndpoint(GetRoomKeyBackupVersionByVersion("1")) {
                    GetRoomKeysBackupVersionResponse.V1(
                        authData = RoomKeyBackupV1AuthData(
                            publicKey = Curve25519KeyValue("keyBackupPublicKey"),
                        ),
                        count = 1,
                        etag = "etag",
                        version = "1"
                    )
                }
            }
            matrixJsonEndpoint(SetCrossSigningKeys) {
                it.request.masterKey shouldNotBe null
                it.request.selfSigningKey shouldNotBe null
                it.request.userSigningKey shouldNotBe null
                setCrossSigningKeysCalled = true
                ResponseWithUIA.Success(Unit)
            }
        }

        scheduleSetup {
            tm.writeTransaction {
                keyStore.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            SignedCrossSigningKeys(
                                CrossSigningKeys(
                                    alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(
                                        Ed25519Key("A_MSK", "A_MSK")
                                    )
                                ), mapOf()
                            ), Valid(false)
                        )
                    )
                }
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(
                                    alice, aliceDevice, setOf(),
                                    keysOf(Ed25519Key(aliceDevice, "dev"))
                                ), mapOf()
                            ),
                            Valid(false)
                        )
                    )
                }
            }
            secretKeyEventContentCalled = false
            defaultSecretKeyEventContentCalled = false
            masterKeyEventContentCalled = false
            userSigningKeyEventContentCalled = false
            selfSigningKeyEventContentCalled = false
            keyBackupEventContentCalled = false
            setRoomKeyBackupVersionCalled = false
            dehydratedDeviceEventContentCalled = false
            setCrossSigningKeysCalled = false

            signServiceMock.returnSignatures = listOf(mapOf(alice to keysOf(Ed25519Key("DEV", "s1"))))
        }
    }

    @Test
    fun `bootstrapCrossSigning » successfull » bootstrap`() = runTest {
        backgroundScope.launch {
            while (currentCoroutineContext().isActive) {
                keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
                tm.writeTransaction {
                    keyStore.updateOutdatedKeys { setOf() }
                }
            }
        }
        backgroundScope.launch {
            keyTrustServiceMock.trustAndSignKeysCalled.filterNotNull().first()
            tm.writeTransaction {
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(
                                    alice, aliceDevice, setOf(),
                                    keysOf(Ed25519Key(aliceDevice, "dev"))
                                ), mapOf()
                            ),
                            KeySignatureTrustLevel.CrossSigned(true)
                        )
                    )
                }
            }
        }
        val result = cut.bootstrapCrossSigning()

        assertSoftly(result) {
            this.recoveryKey shouldNot beEmpty()
            this.result.getOrThrow() shouldBe UIA.Success(Unit)
        }
        keyTrustServiceMock.trustAndSignKeysCalled.value shouldBe (setOf(
            Ed25519Key("A_MSK", "A_MSK"),
            Ed25519Key(aliceDevice, "dev")
        ) to alice)
        keyStore.getSecrets().keys shouldBe setOf(
            M_CROSS_SIGNING_SELF_SIGNING,
            M_CROSS_SIGNING_USER_SIGNING,
            M_DEHYDRATED_DEVICE,
            M_MEGOLM_BACKUP_V1,
        )
        secretKeyEventContentCalled shouldBe true
        capturedPassphrase shouldBe null
        defaultSecretKeyEventContentCalled shouldBe true
        masterKeyEventContentCalled shouldBe true
        userSigningKeyEventContentCalled shouldBe true
        selfSigningKeyEventContentCalled shouldBe true
        keyBackupEventContentCalled shouldBe true
        setRoomKeyBackupVersionCalled shouldBe true
        dehydratedDeviceEventContentCalled shouldBe true
        setCrossSigningKeysCalled shouldBe true
    }

    @Test
    fun `bootstrapCrossSigning » successfull » bootstrap from passphrase`() = runTest {
        backgroundScope.launch {
            while (currentCoroutineContext().isActive) {
                keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
                tm.writeTransaction {
                    keyStore.updateOutdatedKeys { setOf() }
                }
            }
        }
        backgroundScope.launch {
            keyTrustServiceMock.trustAndSignKeysCalled.filterNotNull().first()
            tm.writeTransaction {
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(
                                    alice, aliceDevice, setOf(),
                                    keysOf(Ed25519Key(aliceDevice, "dev"))
                                ), mapOf()
                            ),
                            KeySignatureTrustLevel.CrossSigned(true)
                        )
                    )
                }
            }
        }
        val result = cut.bootstrapCrossSigningFromPassphrase("super secret. not.")
        assertSoftly(result) {
            this.recoveryKey shouldNot beEmpty()
            this.result shouldBe Result.success(UIA.Success(Unit))
        }
        keyTrustServiceMock.trustAndSignKeysCalled.value shouldBe (setOf(
            Ed25519Key("A_MSK", "A_MSK"),
            Ed25519Key(aliceDevice, "dev")
        ) to alice)
        keyStore.getSecrets().keys shouldBe setOf(
            M_CROSS_SIGNING_SELF_SIGNING,
            M_CROSS_SIGNING_USER_SIGNING,
            M_MEGOLM_BACKUP_V1,
            M_DEHYDRATED_DEVICE,
        )
        secretKeyEventContentCalled shouldBe true
        capturedPassphrase.shouldBeInstanceOf<AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2>()
        defaultSecretKeyEventContentCalled shouldBe true
        masterKeyEventContentCalled shouldBe true
        userSigningKeyEventContentCalled shouldBe true
        selfSigningKeyEventContentCalled shouldBe true
        keyBackupEventContentCalled shouldBe true
        setRoomKeyBackupVersionCalled shouldBe true
        dehydratedDeviceEventContentCalled shouldBe true
        setCrossSigningKeysCalled shouldBe true
    }


}
