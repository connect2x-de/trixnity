package de.connect2x.trixnity.client.rtc.livekit

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.getInMemoryRoomTimelineStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.OlmEventHandlerMock
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.rtc.LiveKitRtcTransport
import de.connect2x.trixnity.core.model.events.m.rtc.PerMemberRtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

@OptIn(MSC4143::class, MSC4195::class)
class LiveKitPerMemberRtcSlotMembershipEncryptionHandlerTest : TrixnityBaseTest() {

    private val cut =
        LiveKitPerMemberRtcSlotMembershipEncryptionHandler(
            roomTimelineStore = getInMemoryRoomTimelineStore(),
            keyStore = getInMemoryKeyStore(),
            olmCryptoStore = getInMemoryOlmStore(),
            olmEventHandler = OlmEventHandlerMock(),
            olmEncryptionService = OlmEncryptionServiceMock(),
            api = mockMatrixClientServerApiClient(),
            currentSyncState = CurrentSyncState(MutableStateFlow(SyncState.RUNNING)),
            clock = testScope.testClock,
            userInfo = UserInfo(UserId("us", "server"), "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        )

    private fun transports(vararg published: RtcTransport) =
        RtcMemberEventContent.Join.RtcTransports(published = published.toList())

    @Test
    fun `supports - non per-member encryption - false`() {
        cut.supports(
            RtcEncryption.Unknown(type = "unknown", raw = JsonObject(emptyMap())),
            transports(LiveKitRtcTransport(url = Url("https://livekit.example.org"))),
        ) shouldBe false
    }

    @Test
    fun `supports - transports null - false`() {
        cut.supports(PerMemberRtcEncryption, null) shouldBe false
    }

    @Test
    fun `supports - transports empty`() {
        cut.supports(PerMemberRtcEncryption, transports()) shouldBe false
    }

    @Test
    fun `supports - non livekit transport - false`() {
        cut.supports(
            PerMemberRtcEncryption,
            transports(RtcTransport.Unknown(type = "unknown", raw = JsonObject(emptyMap()))),
        ) shouldBe false
    }

    @Test
    fun `supports - exactly one livekit transport - true`() {
        cut.supports(
            PerMemberRtcEncryption,
            transports(LiveKitRtcTransport(url = Url("https://livekit.example.org"))),
        ) shouldBe true
    }

    @Test
    fun `supports - at least one livekit transport - true`() {
        cut.supports(
            PerMemberRtcEncryption,
            transports(
                RtcTransport.Unknown(type = "unknown", raw = JsonObject(emptyMap())),
                LiveKitRtcTransport(url = Url("https://livekit.example.org")),
            ),
        ) shouldBe true
    }

    @Test
    fun `generateEphemeralMediaKey - creates a 32 byte key`() = runTest {
        cut.generateEphemeralMediaKey().size shouldBe 32
    }
}
