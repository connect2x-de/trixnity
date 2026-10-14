package de.connect2x.trixnity.client.rtc.livekit

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.rtc.PerMemberRtcSlotMembershipEncryptionHandler
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.RoomTimelineStore
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.events.m.rtc.LiveKitRtcTransport
import de.connect2x.trixnity.core.model.events.m.rtc.PerMemberRtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmEventHandler
import kotlin.time.Clock

@MSC4143
@MSC4195
class LiveKitPerMemberRtcSlotMembershipEncryptionHandler(
    roomTimelineStore: RoomTimelineStore,
    keyStore: KeyStore,
    olmCryptoStore: OlmCryptoStore,
    olmEventHandler: OlmEventHandler,
    olmEncryptionService: OlmEncryptionService,
    api: MatrixClientServerApiClient,
    currentSyncState: CurrentSyncState,
    clock: Clock,
    userInfo: UserInfo,
) :
    PerMemberRtcSlotMembershipEncryptionHandler(
        roomTimelineStore = roomTimelineStore,
        keyStore = keyStore,
        olmCryptoStore = olmCryptoStore,
        olmEventHandler = olmEventHandler,
        olmEncryptionService = olmEncryptionService,
        api = api,
        currentSyncState = currentSyncState,
        clock = clock,
        userInfo = userInfo,
    ) {
    override fun supports(encryption: RtcEncryption, transports: RtcMemberEventContent.Join.RtcTransports?): Boolean =
        encryption is PerMemberRtcEncryption && transports?.published?.any { it is LiveKitRtcTransport } == true

    override suspend fun generateEphemeralMediaKey(): ByteArray = SecureRandom.nextBytes(32)
}
