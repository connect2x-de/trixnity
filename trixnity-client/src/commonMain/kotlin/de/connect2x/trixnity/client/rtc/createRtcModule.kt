package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.client.rtc.livekit.LiveKitRtcSlotMembershipDelegateLeaveHandler
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import kotlinx.coroutines.flow.Flow
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

@MSC4143
@OptIn(MSC4354::class, MSC4195::class)
fun createRtcModule() = module {
    single {
            RtcServiceImpl(
                roomStateStore = get(),
                stickyEventStore = get(),
                rtcSlotMembershipFactory = get(),
                keyService = get(),
            )
        }
        .apply {
            bind<RtcService>()
            bind<EventHandler>()
            named<RtcService>()
        }
    single<RtcSlotMembershipFactory> {
        object : RtcSlotMembershipFactory {
            override fun create(
                roomId: RoomId,
                slotId: RtcSlotId,
                slotEventId: EventId,
                memberId: RtcMemberId,
                application: RtcApplicationMember,
                transports: RtcMemberEventContent.Join.RtcTransports?,
                config: RtcSlotMembership.Config,
                slotEvent: Flow<ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>?>,
                memberEvents:
                    Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>,
                onClose: () -> Unit,
            ) =
                RtcSlotMembershipImpl(
                    roomId = roomId,
                    slotId = slotId,
                    slotEventId = slotEventId,
                    memberId = memberId,
                    application = application,
                    transports = transports,
                    config = config,
                    slotEvent = slotEvent,
                    memberEvents = memberEvents,
                    onClose = onClose,
                    delegateLeaveHandlers = getAll(),
                    roomEventEncryptionServices = getAll(),
                    api = get(),
                    currentSyncState = get(),
                    serverDataStore = get(),
                    userInfo = get(),
                    clock = get(),
                    coroutineScope = get(),
                )
        }
    }
    singleOf(::LiveKitRtcSlotMembershipDelegateLeaveHandler) {
        bind<RtcSlotMembershipDelegateLeaveHandler>()
        named<LiveKitRtcSlotMembershipDelegateLeaveHandler>()
    }
}
