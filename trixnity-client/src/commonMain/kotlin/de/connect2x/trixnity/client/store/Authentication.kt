package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.clientserverapi.client.LogoutInfo
import kotlinx.serialization.Serializable

@Serializable data class Authentication(val providerId: String, val providerData: String, val logoutInfo: LogoutInfo?)
