package de.connect2x.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = OAuth2AccountManagementAction.Serializer::class)
sealed interface OAuth2AccountManagementAction {
    val value: String

    object ViewProfile : OAuth2AccountManagementAction {
        override val value: String = "org.matrix.profile"
    }

    object ListDevices : OAuth2AccountManagementAction {
        override val value: String = "org.matrix.devices_list"
    }

    object ViewDevice : OAuth2AccountManagementAction {
        override val value: String = "org.matrix.device_view"
    }

    object DeleteDevice : OAuth2AccountManagementAction {
        override val value: String = "org.matrix.device_delete"
    }

    object DeactivateAccount : OAuth2AccountManagementAction {
        override val value: String = "org.matrix.account_deactivate"
    }

    object ResetCrossSigning : OAuth2AccountManagementAction {
        override val value: String = "org.matrix.cross_signing_reset"
    }

    data class Unknown(override val value: String) : OAuth2AccountManagementAction

    object Serializer : KSerializer<OAuth2AccountManagementAction> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor =
            buildSerialDescriptor("OAuth2AccountManagementAction", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): OAuth2AccountManagementAction =
            when (val value = decoder.decodeString().lowercase()) {
                ViewProfile.value -> ViewProfile
                ListDevices.value -> ListDevices
                ViewDevice.value -> ViewDevice
                DeleteDevice.value -> DeleteDevice
                DeactivateAccount.value -> DeactivateAccount
                ResetCrossSigning.value -> ResetCrossSigning
                else -> Unknown(value)
            }

        override fun serialize(encoder: Encoder, value: OAuth2AccountManagementAction) =
            encoder.encodeString(value.value)
    }
}
