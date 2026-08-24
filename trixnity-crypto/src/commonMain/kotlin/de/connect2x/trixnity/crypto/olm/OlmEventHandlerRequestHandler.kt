package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.Keys

interface OlmEventHandlerRequestHandler {
    suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit>

    suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String,
    ): Result<Unit>
}
