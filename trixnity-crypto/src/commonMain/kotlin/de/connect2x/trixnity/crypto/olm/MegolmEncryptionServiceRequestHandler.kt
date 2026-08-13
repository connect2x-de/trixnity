package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent

interface MegolmEncryptionServiceRequestHandler {
    suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String
    ): Result<Unit>
}
