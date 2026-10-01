package de.connect2x.trixnity.client.integrationtests

import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.firstWithContent
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.client.verification
import de.connect2x.trixnity.client.verification.VerificationService.SelfVerificationMethods
import de.connect2x.trixnity.clientserverapi.client.UIA
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.INVITE
import de.connect2x.trixnity.core.model.events.m.room.Membership.JOIN
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
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
class KeyShareIT : TrixnityBaseTest() {

    private lateinit var startedClient1: StartedClient
    private lateinit var startedClient2: StartedClient

    @Container val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val baseUrl =
            URLBuilder(
                    protocol = URLProtocol.HTTP,
                    host = synapseDocker.host,
                    port = synapseDocker.firstMappedPort,
                )
                .build()
        startedClient1 = registerAndStartClient("client1", "user1", baseUrl, RepositoriesModule.exposed(newDatabase()))
        startedClient2 = registerAndStartClient("client2", "user2", baseUrl, RepositoriesModule.exposed(newDatabase()))
    }

    @AfterTest
    fun afterEach() {
        startedClient1.client.close()
        startedClient2.client.close()
    }

    @Test
    fun testKeyShare(): Unit =
        runBlocking(Dispatchers.Default) {
            withTimeout(30.seconds) {
                startedClient1.client.verification
                    .getSelfVerificationMethods()
                    .filterIsInstance<SelfVerificationMethods.NoCrossSigningEnabled>()
                    .firstWithTimeout()

                withCluePrintln("bootstrap") {
                    startedClient1.client.key
                        .bootstrapCrossSigning()
                        .result
                        .getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                    startedClient2.client.key
                        .bootstrapCrossSigning()
                        .result
                        .getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
                val roomId =
                    withCluePrintln("user1 creates encrypted room with shared history") {
                        startedClient1.client.api.room
                            .createRoom(
                                initialState =
                                    listOf(
                                        InitialStateEvent(content = EncryptionEventContent(), ""),
                                        InitialStateEvent(
                                            HistoryVisibilityEventContent(
                                                HistoryVisibilityEventContent.HistoryVisibility.SHARED
                                            ),
                                            "",
                                        ),
                                    )
                            )
                            .getOrThrow()
                            .also {
                                startedClient1.client.room.getById(it).firstWithTimeout {
                                    it != null && it.membership == JOIN && it.encrypted
                                }
                            }
                    }
                val eventId =
                    withCluePrintln("user1 sends a message") {
                        startedClient1.client.room.sendMessage(roomId) { text("hi from client1") }
                        startedClient1.client.room
                            .getOutbox(roomId)
                            .firstWithTimeout { it.isNotEmpty() }
                            .first()
                            .firstWithTimeout { it?.eventId != null }
                            ?.eventId
                    }
                checkNotNull(eventId)

                withCluePrintln("user1 invites user2") {
                    startedClient1.client.key.shareRoomKeyBundle(roomId, startedClient2.client.userId).getOrThrow()
                    startedClient1.client.api.room.inviteUser(roomId, startedClient2.client.userId).getOrThrow()
                    startedClient2.client.room.getById(roomId).firstWithTimeout {
                        it != null && it.membership == INVITE
                    }
                }

                withCluePrintln("user2 joins") {
                    startedClient2.client.api.room.joinRoom(roomId).getOrThrow()
                    startedClient2.client.room.getById(roomId).firstWithTimeout { it != null && it.membership == JOIN }
                }

                withCluePrintln("user1 can read historic message") {
                    val content =
                        startedClient2.client.room
                            .getTimelineEvent(roomId, eventId)
                            .firstWithContent()
                            .content
                            ?.getOrThrow()
                    content.shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                    content.body shouldBe "hi from client1"
                }
            }
        }
}
