package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService.DecryptMegolmError
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.SerializationException

class FirstWithKeysTest : TrixnityBaseTest() {
    private val roomId = RoomId("!room:server")

    private fun timelineEvent(
        id: String = "1",
        content: Result<RoomEventContent>? = Result.success(RoomMessageEventContent.TextBased.Text("text")),
    ): TimelineEvent =
        TimelineEvent(
            event =
                MessageEvent(
                    RoomMessageEventContent.TextBased.Text(id),
                    EventId(id),
                    UserId("sender", "server"),
                    roomId,
                    1234,
                ),
            content = content,
            gap = null,
            nextEventId = null,
            previousEventId = null,
        )

    @Test
    fun `return first event with successful content`() = runTest {
        val event = timelineEvent(id = "1")
        flowOf(event).firstWithKeys() shouldBe event
    }

    @Test
    fun `ignore null emissions`() = runTest {
        val event = timelineEvent(id = "1")
        flowOf(null, null, event).firstWithKeys() shouldBe event
    }

    @Test
    fun `ignore events with null content`() = runTest {
        val eventWithoutContent = timelineEvent(id = "1", content = null)
        val eventWithContent = timelineEvent(id = "2")
        flowOf(eventWithoutContent, eventWithContent).firstWithKeys() shouldBe eventWithContent
    }

    @Test
    fun `ignore events with MegolmKeyNotFound error`() = runTest {
        val eventKeyNotFound =
            timelineEvent(
                id = "1",
                content = Result.failure(RoomEventEncryptionServiceError(DecryptMegolmError.MegolmKeyNotFound())),
            )
        val eventSuccess = timelineEvent(id = "2")
        flowOf(eventKeyNotFound, eventSuccess).firstWithKeys() shouldBe eventSuccess
    }

    @Test
    fun `ignore events with MegolmKeyUnknownMessageIndex error`() = runTest {
        val eventKeyUnknownIndex =
            timelineEvent(
                id = "1",
                content =
                    Result.failure(RoomEventEncryptionServiceError(DecryptMegolmError.MegolmKeyUnknownMessageIndex())),
            )
        val eventSuccess = timelineEvent(id = "2")
        flowOf(eventKeyUnknownIndex, eventSuccess).firstWithKeys() shouldBe eventSuccess
    }

    @Test
    fun `return event on CryptoDriverError`() = runTest {
        val event =
            timelineEvent(
                id = "1",
                content =
                    Result.failure(
                        RoomEventEncryptionServiceError(
                            DecryptMegolmError.CryptoDriverError(
                                CryptoDriverException(RuntimeException("driver error"))
                            )
                        )
                    ),
            )
        flowOf(event).firstWithKeys() shouldBe event
    }

    @Test
    fun `return event on DeserializationError`() = runTest {
        val event =
            timelineEvent(
                id = "1",
                content =
                    Result.failure(
                        RoomEventEncryptionServiceError(
                            DecryptMegolmError.DeserializationError(SerializationException("deserialization error"))
                        )
                    ),
            )
        flowOf(event).firstWithKeys() shouldBe event
    }

    @Test
    fun `return event on MegolmKeyWithheld`() = runTest {
        val event =
            timelineEvent(
                id = "1",
                content =
                    Result.failure(
                        RoomEventEncryptionServiceError(
                            DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified")
                        )
                    ),
            )
        flowOf(event).firstWithKeys() shouldBe event
    }

    @Test
    fun `return event on ValidationFailed`() = runTest {
        val event =
            timelineEvent(
                id = "1",
                content =
                    Result.failure(
                        RoomEventEncryptionServiceError(DecryptMegolmError.ValidationFailed("validation failed"))
                    ),
            )
        flowOf(event).firstWithKeys() shouldBe event
    }

    @Test
    fun `return event when RoomEventEncryptionServiceError cause is not DecryptMegolmError`() = runTest {
        val event =
            timelineEvent(
                id = "1",
                content = Result.failure(RoomEventEncryptionServiceError(IllegalStateException("other error"))),
            )
        flowOf(event).firstWithKeys() shouldBe event
    }

    @Test
    fun `return event when exception is not RoomEventEncryptionServiceError`() = runTest {
        val event = timelineEvent(id = "1", content = Result.failure(IllegalStateException("arbitrary failure")))
        flowOf(event).firstWithKeys() shouldBe event
    }

    @Test
    fun `wait across state changes until matching event is emitted`() = runTest {
        val stateFlow = MutableStateFlow<TimelineEvent?>(null)
        val deferred = async { stateFlow.firstWithKeys() }

        stateFlow.value = null
        stateFlow.value = timelineEvent(id = "1", content = null)
        stateFlow.value =
            timelineEvent(
                id = "2",
                content = Result.failure(RoomEventEncryptionServiceError(DecryptMegolmError.MegolmKeyNotFound())),
            )
        stateFlow.value =
            timelineEvent(
                id = "3",
                content =
                    Result.failure(RoomEventEncryptionServiceError(DecryptMegolmError.MegolmKeyUnknownMessageIndex())),
            )

        val successEvent = timelineEvent(id = "4")
        stateFlow.value = successEvent

        deferred.await() shouldBe successEvent
    }

    @Test
    fun `wait across state changes until error with withheld key is emitted`() = runTest {
        val stateFlow = MutableStateFlow<TimelineEvent?>(null)
        val deferred = async { stateFlow.firstWithKeys() }

        stateFlow.value =
            timelineEvent(
                id = "1",
                content = Result.failure(RoomEventEncryptionServiceError(DecryptMegolmError.MegolmKeyNotFound())),
            )

        val withheldEvent =
            timelineEvent(
                id = "2",
                content =
                    Result.failure(
                        RoomEventEncryptionServiceError(
                            DecryptMegolmError.MegolmKeyWithheld(RoomKeyWithheldCode.Unverified, "unverified")
                        )
                    ),
            )
        stateFlow.value = withheldEvent

        deferred.await() shouldBe withheldEvent
    }
}
