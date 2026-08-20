package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import kotlin.time.Instant

interface OlmStore {
    suspend fun getDeviceKeys(userId: UserId): Map<String, SignedDeviceKeys>?
    suspend fun getMembers(roomId: RoomId, memberships: Set<Membership>): Set<UserId>
    suspend fun getTrustLevel(userId: UserId, deviceId: String): DeviceTrustLevel?

    suspend fun getOlmSessions(identityKeyValue: Curve25519KeyValue): Set<StoredOlmSession>?
    suspend fun updateOlmSessions(
        identityKeyValue: Curve25519KeyValue,
        updater: (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    )

    suspend fun getOutboundMegolmSession(
        roomId: RoomId,
    ): StoredOutboundMegolmSession?

    suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    )

    suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    )

    suspend fun getInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
    ): StoredInboundMegolmSession?

    suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    )

    suspend fun getOlmAccount(): String
    suspend fun updateOlmAccount(updater: (String) -> String)
    suspend fun getOlmPickleKey(): String?
    suspend fun getForgetFallbackKeyAfter(): Instant?
    suspend fun updateForgetFallbackKeyAfter(updater: (Instant?) -> Instant?)

    suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility?

    suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm?
}

internal suspend fun OlmStore.findDeviceKeys(userId: UserId, senderKeyValue: Curve25519KeyValue): SignedDeviceKeys? =
    getDeviceKeys(userId)?.values
        ?.find { it.signed.keys.keys.any { key -> key.value == senderKeyValue } }

internal suspend fun OlmStore.getDevices(roomId: RoomId, memberships: Set<Membership>): Set<Pair<UserId, String>> =
    getMembers(roomId, memberships).mapNotNull { userId ->
        getDeviceKeys(userId)?.let { userId to it.keys }
    }.flatMap { (userId, deviceIds) -> deviceIds.map { userId to it } }
        .toSet()
