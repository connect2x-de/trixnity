package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.eventually
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryInboundMegolmMessageIndexRepository
import de.connect2x.trixnity.client.store.repository.InMemoryInboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOlmAccountRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOlmForgetFallbackKeyAfterRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOlmSessionRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOutboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.client.store.repository.OlmAccountRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class OlmStoreTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val olmAccountRepository = InMemoryOlmAccountRepository() as OlmAccountRepository
    private val inboundMegolmSessionRepository =
        InMemoryInboundMegolmSessionRepository() as InboundMegolmSessionRepository

    private val cut =
        OlmCryptoStore(
            olmAccountRepository = olmAccountRepository,
            olmForgetFallbackKeyAfterRepository = InMemoryOlmForgetFallbackKeyAfterRepository(),
            olmSessionRepository = InMemoryOlmSessionRepository(),
            inboundMegolmSessionRepository = inboundMegolmSessionRepository,
            inboundMegolmMessageIndexRepository = InMemoryInboundMegolmMessageIndexRepository(),
            outboundMegolmSessionRepository = InMemoryOutboundMegolmSessionRepository(),
            tm = tm,
            config = MatrixClientConfiguration(),
            statisticCollector = ObservableCacheStatisticCollector(),
            storeScope = testScope.backgroundScope,
            clock = testScope.testClock,
        )

    private val session =
        StoredInboundMegolmSession(
            senderKey = Curve25519KeyValue("senderCurve"),
            sessionId = "session",
            roomId = RoomId("!room:server"),
            firstKnownIndex = 24,
            hasBeenBackedUp = false,
            isTrusted = true,
            senderSigningKey = Ed25519KeyValue("edKey"),
            forwardingCurve25519KeyChain = listOf(),
            pickled = "pickle",
        )

    @Test
    fun `init » load values from database`() = runTest {
        tm.writeTransaction { olmAccountRepository.save(1, "olm_account") }

        cut.init(this)

        tm.writeTransaction { cut.updateOlmAccount { "olm_account" } }
    }

    @Test
    fun `init » start job which saves changes to database and fills notBackedUp inbound megolm sessions`() = runTest {
        tm.writeTransaction {
            inboundMegolmSessionRepository.save(
                InboundMegolmSessionRepositoryKey(sessionId = "session1", roomId = RoomId("!room:server")),
                StoredInboundMegolmSession(
                    senderKey = Curve25519KeyValue("senderCurve1"),
                    senderSigningKey = Ed25519KeyValue("senderEd1"),
                    sessionId = "session1",
                    roomId = RoomId("!room:server"),
                    firstKnownIndex = 1,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = "pickled1",
                ),
            )
            inboundMegolmSessionRepository.save(
                InboundMegolmSessionRepositoryKey(sessionId = "session2", roomId = RoomId("!room:server")),
                StoredInboundMegolmSession(
                    senderKey = Curve25519KeyValue("senderCurve2"),
                    senderSigningKey = Ed25519KeyValue("senderEd2"),
                    sessionId = "session2",
                    roomId = RoomId("!room:server"),
                    firstKnownIndex = 1,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = "pickled2",
                ),
            )
        }

        cut.init(this)

        tm.writeTransaction { cut.updateOlmAccount { "olm_account" } }

        eventually(5.seconds) {
            tm.writeTransaction { olmAccountRepository.get(1) shouldBe "olm_account" }
            cut.notBackedUpInboundMegolmSessions.value.size shouldBe 2
        }
    }

    @Test
    fun `updateInboundMegolmSession » add and remove to notBackedUpInboundMegolmSessions`() = runTest {
        tm.writeTransaction {
            cut.updateInboundMegolmSession(session.sessionId, session.roomId) { session }
            cut.notBackedUpInboundMegolmSessions.value.values shouldBe setOf(session)
            cut.updateInboundMegolmSession(session.sessionId, session.roomId) { session.copy(hasBeenBackedUp = true) }
        }
        cut.notBackedUpInboundMegolmSessions.value.values.shouldBeEmpty()
    }

    @Test
    fun `updateOlmAccount » handle rollback`() = runTest {
        val olmAccountRepositoryMock =
            object : OlmAccountRepository {
                context(transaction: ReadTransaction)
                override suspend fun get(key: Long): String? = "old_account"

                context(transaction: WriteTransaction)
                override suspend fun save(key: Long, value: String) {
                    throw RuntimeException("upsi")
                }

                context(transaction: WriteTransaction)
                override suspend fun delete(key: Long) {}

                context(transaction: WriteTransaction)
                override suspend fun deleteAll() {}
            }

        val cut =
            OlmCryptoStore(
                olmAccountRepository = olmAccountRepositoryMock,
                olmForgetFallbackKeyAfterRepository = InMemoryOlmForgetFallbackKeyAfterRepository(),
                olmSessionRepository = InMemoryOlmSessionRepository(),
                inboundMegolmSessionRepository = inboundMegolmSessionRepository,
                inboundMegolmMessageIndexRepository = InMemoryInboundMegolmMessageIndexRepository(),
                outboundMegolmSessionRepository = InMemoryOutboundMegolmSessionRepository(),
                tm = tm,
                config = MatrixClientConfiguration(),
                statisticCollector = ObservableCacheStatisticCollector(),
                storeScope = testScope.backgroundScope,
                clock = testScope.testClock,
            )
        cut.init(this)
        shouldThrow<RuntimeException> { tm.writeTransaction { cut.updateOlmAccount { "new_account" } } }
            .message shouldBe "upsi"
        cut.getOlmAccount() shouldBe "old_account"
    }
}
