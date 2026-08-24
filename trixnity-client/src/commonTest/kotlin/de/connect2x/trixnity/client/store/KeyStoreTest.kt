package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.eventually
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.CrossSigningKeysRepository
import de.connect2x.trixnity.client.store.repository.DeviceKeysRepository
import de.connect2x.trixnity.client.store.repository.InMemoryCrossSigningKeysRepository
import de.connect2x.trixnity.client.store.repository.InMemoryDeviceKeysRepository
import de.connect2x.trixnity.client.store.repository.InMemoryKeyChainLinkRepository
import de.connect2x.trixnity.client.store.repository.InMemoryKeyVerificationStateRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOutdatedKeysRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.InMemorySecretKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.InMemorySecretsRepository
import de.connect2x.trixnity.client.store.repository.KeyChainLinkRepository
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateRepository
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.client.store.repository.OutdatedKeysRepository
import de.connect2x.trixnity.client.store.repository.RoomKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.SecretKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.SecretsRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.core.model.events.m.KeyRequestAction
import de.connect2x.trixnity.core.model.events.m.RoomKeyRequestEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class KeyStoreTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val outdatedKeysRepository = InMemoryOutdatedKeysRepository() as OutdatedKeysRepository
    private val deviceKeysRepository = InMemoryDeviceKeysRepository() as DeviceKeysRepository
    private val crossSigningKeysRepository = InMemoryCrossSigningKeysRepository() as CrossSigningKeysRepository
    private val keyVerificationStateRepository =
        InMemoryKeyVerificationStateRepository() as KeyVerificationStateRepository
    private val keyChainLinkRepository = InMemoryKeyChainLinkRepository() as KeyChainLinkRepository
    private val secretsRepository = InMemorySecretsRepository() as SecretsRepository
    private val secretKeyRequestRepository = InMemorySecretKeyRequestRepository() as SecretKeyRequestRepository
    private val roomKeyRequestRepository = InMemoryRoomKeyRequestRepository() as RoomKeyRequestRepository

    private val cut =
        KeyStore(
            outdatedKeysRepository = outdatedKeysRepository,
            deviceKeysRepository = deviceKeysRepository,
            crossSigningKeysRepository = crossSigningKeysRepository,
            keyVerificationStateRepository = keyVerificationStateRepository,
            keyChainLinkRepository = keyChainLinkRepository,
            secretsRepository = secretsRepository,
            secretKeyRequestRepository = secretKeyRequestRepository,
            roomKeyRequestRepository = roomKeyRequestRepository,
            tm = tm,
            config = MatrixClientConfiguration(),
            statisticCollector = ObservableCacheStatisticCollector(),
            storeScope = testScope.backgroundScope,
            clock = testScope.testClock,
        )

    @Test
    fun `init » load values from database`() = runTest {
        val storedSecretKeyRequest =
            StoredSecretKeyRequest(
                SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
                setOf("DEV1", "DEV2"),
                Instant.fromEpochMilliseconds(1234),
            )
        val storedRoomKeyRequest =
            StoredRoomKeyRequest(
                RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
                setOf("DEV1", "DEV2"),
                Instant.fromEpochMilliseconds(1234),
            )
        tm.writeTransaction {
            outdatedKeysRepository.save(1, setOf(UserId("alice", "server"), UserId("bob", "server")))
            secretsRepository.save(
                1,
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to
                        StoredSecret(GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s")
                ),
            )
            secretKeyRequestRepository.save("1", storedSecretKeyRequest)
            roomKeyRequestRepository.save("1", storedRoomKeyRequest)
        }

        cut.getOutdatedKeysFlow().first() shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
        cut.getSecrets() shouldBe
            mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to
                    StoredSecret(GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s")
            )
        cut.getAllSecretKeyRequestsFlow().first { it.isNotEmpty() }
        cut.getAllSecretKeyRequestsFlow().first() shouldBe listOf(storedSecretKeyRequest)

        cut.getAllRoomKeyRequestsFlow().first { it.isNotEmpty() }
        cut.getAllRoomKeyRequestsFlow().first() shouldBe listOf(storedRoomKeyRequest)
    }

    @Test
    fun `init » start job which saves changes to database`() = runTest {
        tm.writeTransaction {
            cut.updateOutdatedKeys { setOf(UserId("alice", "server"), UserId("bob", "server")) }
            cut.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to
                        StoredSecret(GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s")
                )
            }
        }

        eventually(5.seconds) {
            tm.readTransaction {
                outdatedKeysRepository.get(1) shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
                secretsRepository.get(1) shouldBe
                    mapOf(
                        SecretType.M_CROSS_SIGNING_USER_SIGNING to
                            StoredSecret(GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s")
                    )
            }
        }
    }

    @Test
    fun `update keys when not known`() = runTest {
        cut.getOutdatedKeys().shouldBeEmpty()
        val getKeysJob = async { getKeys() }
        cut.getOutdatedKeysFlow().first { it.isNotEmpty() }
        tm.writeTransaction { cut.updateOutdatedKeys { emptySet() } }
        getKeysJob.join()
    }

    @Test
    fun `not update keys when context forbids it`() = runTest {
        cut.getOutdatedKeys().shouldBeEmpty()
        withContext(KeyStore.SkipOutdatedKeys) { getKeys() }
        cut.getOutdatedKeys().shouldBeEmpty()
    }

    private suspend fun getKeys() = cut.getDeviceKeys(UserId("alice", "server")).first()
}
