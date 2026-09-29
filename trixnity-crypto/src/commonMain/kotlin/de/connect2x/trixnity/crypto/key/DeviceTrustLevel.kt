package de.connect2x.trixnity.crypto.key

sealed interface DeviceTrustLevel {

    /** The device key is valid, but not cross signed. */
    data class Valid(val verified: Boolean) : DeviceTrustLevel

    /** The device key is cross signed. */
    data class CrossSigned(val verified: Boolean) : DeviceTrustLevel

    /** There is a master key, but the device key has not been cross signed yet. */
    data object NotCrossSigned : DeviceTrustLevel

    /** The device key or a key, that signed this device key is blocked. */
    data object Blocked : DeviceTrustLevel

    /** The trust level could not be calculated. */
    data class Invalid(val reason: String) : DeviceTrustLevel

    /** There are no information for this device. */
    data object Unknown : DeviceTrustLevel
}
