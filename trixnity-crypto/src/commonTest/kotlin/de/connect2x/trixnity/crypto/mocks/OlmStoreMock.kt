package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.olm.OlmStore
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class OlmStoreMock : OlmStore {
    val devices: MutableMap<UserId, Map<String, SignedDeviceKeys>> = mutableMapOf()

    override suspend fun getDeviceKeys(userId: UserId): Map<String, SignedDeviceKeys>? = devices[userId]

    val roomMembers = mutableMapOf<RoomId, Set<UserId>>()

    override suspend fun getMembers(roomId: RoomId, memberships: Set<Membership>): Set<UserId> =
        roomMembers[roomId].orEmpty()

    val deviceTrustLevels: MutableMap<UserId, Map<String, DeviceTrustLevel>> = mutableMapOf()

    override suspend fun getTrustLevel(userId: UserId, deviceId: String): DeviceTrustLevel? =
        deviceTrustLevels[userId]?.get(deviceId)

    val olmSessions = mutableMapOf<Curve25519KeyValue, Set<StoredOlmSession>?>()

    override suspend fun getOlmSessions(identityKeyValue: Curve25519KeyValue): Set<StoredOlmSession>? =
        olmSessions[identityKeyValue]

    override suspend fun updateOlmSessions(
        identityKeyValue: Curve25519KeyValue,
        updater: (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?,
    ) {
        olmSessions[identityKeyValue] = updater(olmSessions[identityKeyValue])
    }

    val outboundMegolmSession = mutableMapOf<RoomId, StoredOutboundMegolmSession?>()

    override suspend fun getOutboundMegolmSession(roomId: RoomId): StoredOutboundMegolmSession? =
        outboundMegolmSession[roomId]

    override suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?,
    ) {
        outboundMegolmSession[roomId] = updater(outboundMegolmSession[roomId])
    }

    val inboundMegolmSession = mutableMapOf<Pair<String, RoomId>, StoredInboundMegolmSession?>()

    override suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?,
    ) {
        inboundMegolmSession[sessionId to roomId] = updater(inboundMegolmSession[sessionId to roomId])
    }

    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? {
        return inboundMegolmSession[sessionId to roomId]
    }

    val inboundMegolmSessionIndex = mutableMapOf<Triple<String, RoomId, Long>, StoredInboundMegolmMessageIndex?>()

    override suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?,
    ) {
        inboundMegolmSessionIndex[Triple(sessionId, roomId, messageIndex)] =
            updater(inboundMegolmSessionIndex[Triple(sessionId, roomId, messageIndex)])
    }

    val olmAccount: MutableStateFlow<String> = MutableStateFlow("")

    override suspend fun getOlmAccount(): String = olmAccount.value

    override suspend fun updateOlmAccount(updater: (String) -> String) {
        olmAccount.update { updater(it) }
    }

    override suspend fun getOlmPickleKey(): String? = null

    val forgetFallbackKeyAfter: MutableStateFlow<Instant?> = MutableStateFlow(null)

    override suspend fun getForgetFallbackKeyAfter(): Instant? = forgetFallbackKeyAfter.value

    override suspend fun updateForgetFallbackKeyAfter(updater: (Instant?) -> Instant?) {
        forgetFallbackKeyAfter.update { updater(it) }
    }

    var historyVisibility: HistoryVisibilityEventContent.HistoryVisibility? = null

    override suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility? {
        return historyVisibility
    }

    val roomEncryptionAlgorithm = mutableMapOf<RoomId, EncryptionAlgorithm?>()

    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? {
        return roomEncryptionAlgorithm[roomId]
    }
}
