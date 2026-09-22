package de.connect2x.trixnity.crypto.key

import de.connect2x.trixnity.core.model.UserId

sealed interface EventTrustLevel {
    /** The trust level of the device originally sent this event */
    val senderTrustLevel: DeviceTrustLevel

    /** The authenticity of the event cannot be calculated. Usually it's because the event is unencrypted. */
    data object Unauthenticated : EventTrustLevel {
        override val senderTrustLevel = DeviceTrustLevel.Unknown
    }

    /** The key comes from the event sender */
    data class Creator(override val senderTrustLevel: DeviceTrustLevel) : EventTrustLevel

    /** The key comes from the unauthenticated key backup */
    data class UnauthenticatedBackup(override val senderTrustLevel: DeviceTrustLevel) : EventTrustLevel

    /** An own cross signed device or another user forwarded the key to us */
    data class Shared(
        override val senderTrustLevel: DeviceTrustLevel,
        /**
         * The trust level of the devices, that forwarded the key. Usually, this cannot be fully trusted, as the
         * original source of the key is unknown.
         */
        val sharingTrustLevels: Map<Sender, DeviceTrustLevel>,
    ) : EventTrustLevel {
        data class Sender(val userId: UserId, val deviceId: String)
    }

    /** There are not enough information to determine the trust level */
    data object Unknown : EventTrustLevel {
        override val senderTrustLevel = DeviceTrustLevel.Unknown
    }
}
