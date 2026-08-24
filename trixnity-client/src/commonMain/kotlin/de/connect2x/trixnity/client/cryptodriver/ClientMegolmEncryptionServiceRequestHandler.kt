package de.connect2x.trixnity.client.cryptodriver

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionServiceRequestHandler

class ClientMegolmEncryptionServiceRequestHandler(private val api: MatrixClientServerApiClient) :
    MegolmEncryptionServiceRequestHandler {
    override suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String,
    ): Result<Unit> = api.user.sendToDevice(events, transactionId)
}
