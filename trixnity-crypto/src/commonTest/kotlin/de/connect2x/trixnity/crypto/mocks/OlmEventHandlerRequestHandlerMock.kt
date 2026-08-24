package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.crypto.olm.OlmEventHandlerRequestHandler

class OlmEventHandlerRequestHandlerMock : OlmEventHandlerRequestHandler {
    val setOneTimeKeysParam = mutableListOf<Pair<Keys?, Keys?>>()
    var setOneTimeKeys: Result<Unit>? = null

    override suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit> {
        setOneTimeKeysParam.add(oneTimeKeys to fallbackKeys)
        return setOneTimeKeys ?: Result.success(Unit)
    }

    val sendToDeviceParams = mutableListOf<Map<UserId, Map<String, ToDeviceEventContent>>>()
    var sendToDevice: Result<Unit>? = null

    override suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String,
    ): Result<Unit> {
        sendToDeviceParams.add(events)
        return sendToDevice ?: Result.success(Unit)
    }
}
