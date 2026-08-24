package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.core.Unsubscriber
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import de.connect2x.trixnity.crypto.olm.OlmEventHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class OlmEventHandlerMock : OlmEventHandler {
    val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber {
        eventSubscribers.update { it + eventSubscriber }
        return { eventSubscribers.update { it - eventSubscriber } }
    }
}
