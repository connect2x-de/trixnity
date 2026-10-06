package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.client.flattenNotNull
import de.connect2x.trixnity.client.key.KeyService
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.StickyEventStore
import de.connect2x.trixnity.client.store.get
import de.connect2x.trixnity.client.store.getByStateKey
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.crypto.key.EventTrustLevel
import kotlin.jvm.JvmName
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@MSC4143
interface RtcService {
    fun getSlot(roomId: RoomId, slotId: RtcSlotId): Flow<ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>?>

    fun getAllSlots(roomId: RoomId): Flow<Map<RtcSlotId, ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>>>

    fun getMembers(
        roomId: RoomId,
        slotId: RtcSlotId,
    ): Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>

    fun getMembers(
        roomId: RoomId
    ): Flow<Map<RtcSlotId, Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>>
}

@MSC4143
@OptIn(MSC4354::class, ExperimentalCoroutinesApi::class)
class RtcServiceImpl(
    private val roomStateStore: RoomStateStore,
    private val stickyEventStore: StickyEventStore,
    private val keyService: KeyService,
) : RtcService, EventHandler {
    override fun getSlot(
        roomId: RoomId,
        slotId: RtcSlotId,
    ): Flow<ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>?> =
        roomStateStore.getByStateKey<EncryptionEventContent>(roomId).flatMapLatest { encryptionEvent ->
            getSlot(roomId, slotId, encryptionEvent != null)
        }

    private fun getSlot(roomId: RoomId, slotId: RtcSlotId, roomIsEncrypted: Boolean) =
        roomStateStore.getByStateKey<RtcSlotEventContent>(roomId, slotId.value).filterTrusted(roomIsEncrypted).map {
            it as? ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>
        }

    override fun getAllSlots(
        roomId: RoomId
    ): Flow<Map<RtcSlotId, ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>>> =
        roomStateStore.getByStateKey<EncryptionEventContent>(roomId).flatMapLatest { encryptionEvent ->
            getAllSlots(roomId, encryptionEvent != null).flattenNotNull(Duration.ZERO)
        }

    private fun getAllSlots(roomId: RoomId, roomIsEncrypted: Boolean) =
        roomStateStore.get<RtcSlotEventContent>(roomId).map { map ->
            map.mapKeys { (key, _) -> RtcSlotId(key) }
                .mapValues { (_, value) ->
                    value.filterTrusted(roomIsEncrypted).map {
                        it as? ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>
                    }
                }
        }

    override fun getMembers(
        roomId: RoomId,
        slotId: RtcSlotId,
    ): Flow<Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>> =
        roomStateStore.getByStateKey<EncryptionEventContent>(roomId).flatMapLatest { encryptionEvent ->
            getSlot(roomId, slotId, encryptionEvent != null)
                .getMembers(roomId, slotId, encryptionEvent != null)
                .flattenNotNull(Duration.ZERO)
        }

    private fun Flow<ClientEvent.RoomEvent.StateEvent<RtcSlotEventContent>?>.getMembers(
        roomId: RoomId,
        slotId: RtcSlotId,
        roomIsEncrypted: Boolean,
    ) = flatMapLatest { slot ->
        if (slot?.content !is RtcSlotEventContent.Open) {
            return@flatMapLatest flowOf(emptyMap())
        }
        stickyEventStore.get<RtcMemberEventContent>(roomId).map {
            it.mapNotNull { (key, value) ->
                    val memberId = key.second?.let(::RtcMemberId) ?: return@mapNotNull null
                    (key.first to memberId) to
                        value
                            .map {
                                val content = it?.content
                                if (
                                    content?.slotId != slotId ||
                                        content.member.id != memberId ||
                                        content is RtcMemberEventContent.Join &&
                                            content.application?.type != slot.content.application?.type
                                )
                                    return@map null
                                else it
                            }
                            .filterTrusted(roomIsEncrypted)
                }
                .toMap()
        }
    }

    override fun getMembers(
        roomId: RoomId
    ): Flow<Map<RtcSlotId, Map<Pair<UserId, RtcMemberId>, ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>>>> =
        roomStateStore.getByStateKey<EncryptionEventContent>(roomId).flatMapLatest { encryptionEvent ->
            getAllSlots(roomId, encryptionEvent != null)
                .map { slots ->
                    slots.mapValues { (slotId, slotFlow) ->
                        slotFlow.getMembers(roomId, slotId, encryptionEvent != null).flattenNotNull(Duration.ZERO)
                    }
                }
                .flattenNotNull(Duration.ZERO)
        }

    @JvmName("filterRtcSlotTrustLevel")
    private fun Flow<ClientEvent.StateBaseEvent<RtcSlotEventContent>?>.filterTrusted(
        roomIsEncrypted: Boolean
    ): Flow<ClientEvent.StateBaseEvent<RtcSlotEventContent>?> = map { rtcSlotEvent ->
        if (rtcSlotEvent == null) return@map null
        val canBeTrusted =
            when (val content = rtcSlotEvent.content) {
                is RtcSlotEventContent.Open ->
                    if (roomIsEncrypted) content.encryption != null else content.encryption == null
                is RtcSlotEventContent.Closed -> true
            }
        if (canBeTrusted) rtcSlotEvent else null
    }

    @JvmName("filterRtcMemberTrustLevel")
    private fun Flow<ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?>.filterTrusted(
        roomIsEncrypted: Boolean
    ): Flow<ClientEvent.RoomEvent.MessageEvent<RtcMemberEventContent>?> = flatMapLatest { rtcMemberEvent ->
        if (rtcMemberEvent == null) return@flatMapLatest flowOf(null)
        val roomId = rtcMemberEvent.roomId
        roomStateStore.getByStateKey<MemberEventContent>(roomId, rtcMemberEvent.sender.full).flatMapLatest { memberEvent
            ->
            if (memberEvent?.content?.membership != Membership.JOIN) return@flatMapLatest flowOf(null)
            keyService.getTrustLevel(roomId, rtcMemberEvent.id).map { eventTrustLevel ->
                val canBeTrusted =
                    when (eventTrustLevel) {
                        EventTrustLevel.Unauthenticated -> !roomIsEncrypted
                        is EventTrustLevel.Creator -> roomIsEncrypted
                        is EventTrustLevel.UnauthenticatedBackup -> roomIsEncrypted
                        is EventTrustLevel.Shared -> roomIsEncrypted
                        EventTrustLevel.Unknown -> false
                    }
                if (canBeTrusted) rtcMemberEvent else null
            }
        }
    }
}
