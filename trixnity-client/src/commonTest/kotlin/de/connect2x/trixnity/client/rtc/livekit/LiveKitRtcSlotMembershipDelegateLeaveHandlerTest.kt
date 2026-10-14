package de.connect2x.trixnity.client.rtc.livekit

import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.model.rtc.livekit.DelegateDelayedLeave
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.LiveKitRtcTransport
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlin.test.Test
import kotlinx.serialization.json.JsonObject

@OptIn(MSC4143::class, MSC4195::class, MSC4193::class)
class LiveKitRtcSlotMembershipDelegateLeaveHandlerTest : TrixnityBaseTest() {

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut =
        LiveKitRtcSlotMembershipDelegateLeaveHandler(
            api = api,
            userInfo = UserInfo(UserId("us", "server"), "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        )

    private val application = CallRtcApplication.Member()

    private fun transports(vararg published: RtcTransport) =
        RtcMemberEventContent.Join.RtcTransports(published = published.toList())

    @Test
    fun `supports » no transports » false`() {
        cut.supports(application = application, transports = null) shouldBe false
    }

    @Test
    fun `supports » empty transports » false`() {
        cut.supports(application = application, transports = transports()) shouldBe false
    }

    @Test
    fun `supports » unsupported transport » false`() {
        cut.supports(
            application = application,
            transports = transports(RtcTransport.Unknown(type = "unknown", raw = JsonObject(emptyMap()))),
        ) shouldBe false
    }

    @Test
    fun `supports » LiveKit transport with foreign server » false`() {
        cut.supports(
            application = application,
            transports = transports(LiveKitRtcTransport(url = Url("https://livekit.example.org"), serverName = "other")),
        ) shouldBe false
    }

    @Test
    fun `supports » LiveKit transport with same server » true`() {
        cut.supports(
            application = application,
            transports =
                transports(LiveKitRtcTransport(url = Url("https://livekit.example.org"), serverName = "server")),
        ) shouldBe true
    }

    @Test
    fun `supports » LiveKit transport » true`() {
        cut.supports(
            application = application,
            transports = transports(LiveKitRtcTransport(url = Url("https://livekit.example.org"))),
        ) shouldBe true
    }

    @Test
    fun `supports » LiveKit transport among other transports » true`() {
        cut.supports(
            application = application,
            transports =
                transports(
                    RtcTransport.Unknown(type = "unknown", raw = JsonObject(emptyMap())),
                    LiveKitRtcTransport(url = Url("https://livekit.example.org")),
                ),
        ) shouldBe true
    }

    @Test
    fun `delegateLeave » for all given transports`() = runTest {
        val roomId = RoomId("room")
        val slotId = RtcSlotId("slot")
        val memberId = RtcMemberId("member")
        val delayId = DelayId("delay")
        val requests = mutableListOf<DelegateDelayedLeave.Request>()
        apiConfig.endpoints { matrixJsonEndpoint(DelegateDelayedLeave) { requests.add(it) } }
        cut.delegateLeave(
                roomId = roomId,
                slotId = slotId,
                memberId = memberId,
                delayId = delayId,
                application = application,
                transports =
                    transports(
                        LiveKitRtcTransport(url = Url("https://livekit1.example.org")),
                        RtcTransport.Unknown(type = "unknown", raw = JsonObject(emptyMap())),
                        LiveKitRtcTransport(url = Url("https://livekit2.example.org")),
                        LiveKitRtcTransport(url = Url("https://livekit3.example.org"), serverName = "other"),
                        LiveKitRtcTransport(url = Url("https://livekit4.example.org"), serverName = "server"),
                    ),
            )
            .getOrThrow()
        requests shouldBe
            listOf(
                DelegateDelayedLeave.Request(Url("https://livekit1.example.org"), roomId, slotId, memberId, delayId),
                DelegateDelayedLeave.Request(Url("https://livekit2.example.org"), roomId, slotId, memberId, delayId),
                DelegateDelayedLeave.Request(Url("https://livekit4.example.org"), roomId, slotId, memberId, delayId),
            )
    }

    @Test
    fun `delegateLeave » success when any success`() = runTest {
        val roomId = RoomId("room")
        val slotId = RtcSlotId("slot")
        val memberId = RtcMemberId("member")
        val delayId = DelayId("delay")
        val requests = mutableListOf<DelegateDelayedLeave.Request>()
        apiConfig.endpoints {
            matrixJsonEndpoint(DelegateDelayedLeave) {
                requests.add(it)
                if (requests.size == 1)
                    throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.BadJson("dino"))
            }
        }
        cut.delegateLeave(
                roomId = roomId,
                slotId = slotId,
                memberId = memberId,
                delayId = delayId,
                application = application,
                transports =
                    transports(
                        LiveKitRtcTransport(url = Url("https://livekit1.example.org")),
                        LiveKitRtcTransport(url = Url("https://livekit2.example.org")),
                    ),
            )
            .getOrThrow()
        requests shouldBe
            listOf(
                DelegateDelayedLeave.Request(Url("https://livekit1.example.org"), roomId, slotId, memberId, delayId),
                DelegateDelayedLeave.Request(Url("https://livekit2.example.org"), roomId, slotId, memberId, delayId),
            )
    }

    @Test
    fun `delegateLeave » failure when all failure`() = runTest {
        val roomId = RoomId("room")
        val slotId = RtcSlotId("slot")
        val memberId = RtcMemberId("member")
        val delayId = DelayId("delay")
        val requests = mutableListOf<DelegateDelayedLeave.Request>()
        val exception = MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.BadJson("dino"))
        apiConfig.endpoints {
            matrixJsonEndpoint(DelegateDelayedLeave) {
                requests.add(it)
                throw exception
            }
        }
        cut.delegateLeave(
                roomId = roomId,
                slotId = slotId,
                memberId = memberId,
                delayId = delayId,
                application = application,
                transports =
                    transports(
                        LiveKitRtcTransport(url = Url("https://livekit1.example.org")),
                        LiveKitRtcTransport(url = Url("https://livekit2.example.org")),
                    ),
            )
            .exceptionOrNull() shouldBe MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.BadJson("dino"))
        listOf(
            DelegateDelayedLeave.Request(Url("https://livekit1.example.org"), roomId, slotId, memberId, delayId),
            DelegateDelayedLeave.Request(Url("https://livekit2.example.org"), roomId, slotId, memberId, delayId),
        )
    }
}
