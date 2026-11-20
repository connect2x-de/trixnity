package de.connect2x.trixnity.client.integrationtests

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.RoomEventEncryptionServiceError
import de.connect2x.trixnity.client.room.getState
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.store.TimelineEvent.TimelineEventContentError
import de.connect2x.trixnity.client.store.repository.inMemory
import de.connect2x.trixnity.client.verification
import de.connect2x.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKey
import de.connect2x.trixnity.client.verification.VerificationService.SelfVerificationMethods
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.UIA
import de.connect2x.trixnity.clientserverapi.client.classicLogin
import de.connect2x.trixnity.clientserverapi.client.classicLoginWith
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.JOIN
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class ExcludeUntrustedDevicesIT : TrixnityBaseTest() {

    private lateinit var baseUrl: Url
    private lateinit var client1: MatrixClient
    private lateinit var client2: MatrixClient
    private lateinit var client3: MatrixClient
    private val password = "user$1passw0rd"

    @Container val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        baseUrl =
            URLBuilder(
                    protocol = URLProtocol.HTTP,
                    host = synapseDocker.host,
                    port = synapseDocker.firstMappedPort,
                )
                .build()

        client1 =
            MatrixClient.create(
                    repositoriesModule = RepositoriesModule.inMemory(),
                    mediaStoreModule = MediaStoreModule.inMemory(),
                    cryptoDriverModule = CryptoDriverModule.vodozemac(),
                    authProviderData =
                        MatrixClientAuthProviderData.classicLoginWith(baseUrl) { it.register("user1", password) }
                            .getOrThrow(),
                    configuration = { name = "client1" },
                )
                .getOrThrow()
        client2 =
            MatrixClient.create(
                    repositoriesModule = RepositoriesModule.inMemory(),
                    mediaStoreModule = MediaStoreModule.inMemory(),
                    cryptoDriverModule = CryptoDriverModule.vodozemac(),
                    authProviderData =
                        MatrixClientAuthProviderData.classicLogin(
                                baseUrl = baseUrl,
                                identifier = IdentifierType.User("user1"),
                                password = password,
                            )
                            .getOrThrow(),
                    configuration = { name = "client2" },
                )
                .getOrThrow()
        client1.startSync()
        client2.startSync()
        client1.syncState.firstWithTimeout { it == SyncState.RUNNING }
        client2.syncState.firstWithTimeout { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        client1.close()
        client2.close()
        if (::client3.isInitialized) client3.close()
    }

    private suspend fun createClient3(excludeNotCrossSignedDevices: Boolean): MatrixClient =
        MatrixClient.create(
                repositoriesModule = RepositoriesModule.inMemory(),
                mediaStoreModule = MediaStoreModule.inMemory(),
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
                authProviderData =
                    MatrixClientAuthProviderData.classicLoginWith(baseUrl) { it.register("user2", password) }
                        .getOrThrow(),
                configuration = {
                    name = "client3"
                    this.excludeUntrustedDevices = excludeNotCrossSignedDevices
                },
            )
            .getOrThrow()
            .also {
                it.startSync()
                it.syncState.firstWithTimeout { state -> state == SyncState.RUNNING }
            }

    @Test
    fun shouldExcludeNotCrossSignedDevicesByDefault(): Unit =
        runBlocking(Dispatchers.Default) {
            withTimeout(30.seconds) {
                client3 = createClient3(excludeNotCrossSignedDevices = true)

                val bootstrap =
                    withCluePrintln("bootstrap cross-signing on client1") {
                        client1.key.bootstrapCrossSigning().also {
                            it.result.getOrThrow().shouldBeInstanceOf<UIA.Success<Unit>>()
                        }
                    }

                val roomId =
                    withCluePrintln("create encrypted room and join with client3") {
                        val roomId =
                            client1.api.room
                                .createRoom(
                                    invite = setOf(client3.userId),
                                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
                                )
                                .getOrThrow()
                        client1.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
                        client2.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
                        client3.api.room.joinRoom(roomId).getOrThrow()
                        client3.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
                        client3.room.getState<EncryptionEventContent>(roomId).firstWithTimeout { it != null }
                        roomId
                    }

                withCluePrintln("wait for client3 to know client1 is cross-signed and client2 is not cross-signed") {
                    client3.key.getTrustLevel(client1.userId, client1.deviceId).firstWithTimeout {
                        it == DeviceTrustLevel.CrossSigned(false)
                    }
                    client3.key.getTrustLevel(client2.userId, client2.deviceId).firstWithTimeout {
                        it == DeviceTrustLevel.NotCrossSigned
                    }
                }

                val firstEventId =
                    withCluePrintln("client3 sends first message") {
                        val txId = client3.room.sendMessage(roomId) { text("secret for cross-signed only") }
                        client3.room.waitForOutboxSent()
                        client3.room
                            .getOutbox(roomId, txId)
                            .firstWithTimeout { it?.eventId != null }
                            ?.eventId
                            .shouldNotBeNull()
                    }

                withCluePrintln("client1 decrypts first message") {
                    client1.room
                        .getTimelineEvent(roomId, firstEventId)
                        .firstWithTimeout { it?.content?.isSuccess == true }
                        ?.content
                        ?.getOrThrow()
                        .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                        .body shouldBe "secret for cross-signed only"
                }

                withCluePrintln("client2 receives withheld error (m.unverified) for first message") {
                    val contentError =
                        client2.room
                            .getTimelineEvent(roomId, firstEventId)
                            .firstWithTimeout {
                                val error = it?.content?.exceptionOrNull()
                                error is TimelineEventContentError.DecryptionError &&
                                    ((error.error as? RoomEventEncryptionServiceError)?.cause
                                        is MegolmEncryptionService.DecryptMegolmError.MegolmKeyWithheld)
                            }
                            ?.content
                            ?.exceptionOrNull()
                            .shouldBeInstanceOf<TimelineEventContentError.DecryptionError>()
                    val withheldError =
                        contentError.error
                            .shouldBeInstanceOf<RoomEventEncryptionServiceError>()
                            .cause
                            .shouldBeInstanceOf<MegolmEncryptionService.DecryptMegolmError.MegolmKeyWithheld>()
                    withheldError.code shouldBe RoomKeyWithheldCode.Unverified
                }

                withCluePrintln("self-verify client2 and wait for client3 to see client2 as cross-signed") {
                    val client2VerificationMethods =
                        client2.verification
                            .getSelfVerificationMethods()
                            .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                            .firstWithTimeout { it.methods.any { m -> m is AesHmacSha2RecoveryKey } }
                            .methods
                    client2VerificationMethods
                        .filterIsInstance<AesHmacSha2RecoveryKey>()
                        .first()
                        .verify(bootstrap.recoveryKey)
                        .getOrThrow()
                    client3.key.getTrustLevel(client2.userId, client2.deviceId).firstWithTimeout {
                        it == DeviceTrustLevel.CrossSigned(false)
                    }
                }

                val secondEventId =
                    withCluePrintln("client3 sends second message after client2 is cross-signed") {
                        val txId = client3.room.sendMessage(roomId) { text("secret after verification") }
                        client3.room.waitForOutboxSent()
                        client3.room
                            .getOutbox(roomId, txId)
                            .firstWithTimeout { it?.eventId != null }
                            ?.eventId
                            .shouldNotBeNull()
                    }

                withCluePrintln("client2 decrypts second message") {
                    client2.room
                        .getTimelineEvent(roomId, secondEventId)
                        .firstWithTimeout { it?.content?.isSuccess == true }
                        ?.content
                        ?.getOrThrow()
                        .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                        .body shouldBe "secret after verification"
                }
            }
        }

    @Test
    fun shouldIncludeNotCrossSignedDevicesWhenDisabled(): Unit =
        runBlocking(Dispatchers.Default) {
            withTimeout(30.seconds) {
                client3 = createClient3(excludeNotCrossSignedDevices = false)

                withCluePrintln("bootstrap cross-signing on client1") {
                    client1.key.bootstrapCrossSigning().result.getOrThrow().shouldBeInstanceOf<UIA.Success<Unit>>()
                }

                val roomId =
                    withCluePrintln("create encrypted room and join with client3") {
                        val roomId =
                            client1.api.room
                                .createRoom(
                                    invite = setOf(client3.userId),
                                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
                                )
                                .getOrThrow()
                        client1.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
                        client2.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
                        client3.api.room.joinRoom(roomId).getOrThrow()
                        client3.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
                        client3.room.getState<EncryptionEventContent>(roomId).firstWithTimeout { it != null }
                        roomId
                    }

                withCluePrintln("wait for client3 to know client1 is cross-signed and client2 is not cross-signed") {
                    client3.key.getTrustLevel(client1.userId, client1.deviceId).firstWithTimeout {
                        it == DeviceTrustLevel.CrossSigned(false)
                    }
                    client3.key.getTrustLevel(client2.userId, client2.deviceId).firstWithTimeout {
                        it == DeviceTrustLevel.NotCrossSigned
                    }
                }

                val eventId =
                    withCluePrintln("client3 sends message") {
                        val txId = client3.room.sendMessage(roomId) { text("secret for all devices") }
                        client3.room.waitForOutboxSent()
                        client3.room
                            .getOutbox(roomId, txId)
                            .firstWithTimeout { it?.eventId != null }
                            ?.eventId
                            .shouldNotBeNull()
                    }

                withCluePrintln("both client1 and not-cross-signed client2 decrypt message") {
                    client1.room
                        .getTimelineEvent(roomId, eventId)
                        .firstWithTimeout { it?.content?.isSuccess == true }
                        ?.content
                        ?.getOrThrow()
                        .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                        .body shouldBe "secret for all devices"

                    client2.room
                        .getTimelineEvent(roomId, eventId)
                        .firstWithTimeout { it?.content?.isSuccess == true }
                        ?.content
                        ?.getOrThrow()
                        .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                        .body shouldBe "secret for all devices"
                }
            }
        }
}
