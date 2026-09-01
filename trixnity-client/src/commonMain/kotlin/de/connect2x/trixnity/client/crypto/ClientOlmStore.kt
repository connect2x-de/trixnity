package de.connect2x.trixnity.client.crypto

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.key.getDeviceKey
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.getByStateKey
import de.connect2x.trixnity.client.store.members
import de.connect2x.trixnity.client.store.toDeviceTrustLevel
import de.connect2x.trixnity.client.user.LoadMembersService
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
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
import de.connect2x.trixnity.crypto.sign.SignService
import kotlin.time.Instant
import kotlinx.coroutines.flow.first

private val log = Logger("de.connect2x.trixnity.client.cryptodriver")

class ClientOlmStore(
    private val accountStore: AccountStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyStore: KeyStore,
    private val roomStateStore: RoomStateStore,
    private val loadMembersService: LoadMembersService,
    private val tm: StoreTransactionManager,
    private val signService: SignService,
    private val userInfo: UserInfo,
) : OlmStore {

    override suspend fun getDeviceKeys(userId: UserId): Map<String, SignedDeviceKeys>? {
        val deviceKeys = keyStore.getDeviceKeys(userId).first()?.mapValues { it.value.value } ?: return null
        if (userId != userInfo.userId) return deviceKeys
        val ownDeviceKeys = deviceKeys[userInfo.deviceId] ?: return deviceKeys
        if (!ownDeviceKeys.signatures.isNullOrEmpty()) return deviceKeys
        log.debug {
            "fallback to manually signed own device keys, because of a historical json bug, signatures may be missing in the database"
        }
        return deviceKeys + (userInfo.deviceId to signService.getSelfSignedDeviceKeys())
    }

    override suspend fun getMembers(roomId: RoomId, memberships: Set<Membership>): Set<UserId> {
        loadMembersService(roomId, true)
        return roomStateStore.members(roomId, memberships)
    }

    override suspend fun getTrustLevel(userId: UserId, deviceId: String): DeviceTrustLevel =
        keyStore.getDeviceKey(userId, deviceId).first()?.trustLevel.toDeviceTrustLevel()

    override suspend fun getOlmSessions(identityKeyValue: Curve25519KeyValue): Set<StoredOlmSession>? =
        olmCryptoStore.getOlmSessions(identityKeyValue)

    override suspend fun updateOlmSessions(
        identityKeyValue: Curve25519KeyValue,
        updater: (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?,
    ) {
        tm.writeTransaction { olmCryptoStore.updateOlmSessions(identityKeyValue, updater) }
    }

    override suspend fun getOutboundMegolmSession(roomId: RoomId): StoredOutboundMegolmSession? =
        olmCryptoStore.getOutboundMegolmSession(roomId)

    override suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?,
    ) {
        tm.writeTransaction { olmCryptoStore.updateOutboundMegolmSession(roomId, updater) }
    }

    override suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?,
    ) {
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmSession(sessionId, roomId, updater) }
    }

    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? =
        olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()

    override suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?,
    ) {
        tm.writeTransaction { olmCryptoStore.updateInboundMegolmMessageIndex(sessionId, roomId, messageIndex, updater) }
    }

    override suspend fun getOlmAccount(): String = checkNotNull(olmCryptoStore.getOlmAccount())

    override suspend fun updateOlmAccount(updater: (String) -> String) = tm.writeTransaction {
        olmCryptoStore.updateOlmAccount { updater(checkNotNull(it)) }
    }

    override suspend fun getOlmPickleKey(): String? = checkNotNull(accountStore.getAccount()).olmPickleKey

    override suspend fun getForgetFallbackKeyAfter(): Instant? = olmCryptoStore.getForgetFallbackKeyAfter()

    override suspend fun updateForgetFallbackKeyAfter(updater: (Instant?) -> Instant?) = tm.writeTransaction {
        olmCryptoStore.updateForgetFallbackKeyAfter(updater)
    }

    override suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility? =
        roomStateStore.getByStateKey<HistoryVisibilityEventContent>(roomId).first()?.content?.historyVisibility

    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? =
        roomStateStore.getByStateKey<EncryptionEventContent>(roomId).first()?.content?.algorithm
}
