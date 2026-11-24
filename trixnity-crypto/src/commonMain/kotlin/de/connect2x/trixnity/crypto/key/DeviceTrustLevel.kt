package de.connect2x.trixnity.crypto.key

import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.model.keys.DeviceKeys

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

// // https://spec.matrix.org/v1.19/client-server-api/#recommended-client-behaviour
@OptIn(MSC3814::class)
fun DeviceKeys.isTrusted(trustLevel: DeviceTrustLevel, excludeUntrusted: Boolean) =
    when (trustLevel) {
        is DeviceTrustLevel.CrossSigned -> true
        is DeviceTrustLevel.Valid -> dehydrated != true
        DeviceTrustLevel.NotCrossSigned -> dehydrated != true && !excludeUntrusted
        DeviceTrustLevel.Blocked -> false
        is DeviceTrustLevel.Invalid -> false
        DeviceTrustLevel.Unknown -> false
    }
