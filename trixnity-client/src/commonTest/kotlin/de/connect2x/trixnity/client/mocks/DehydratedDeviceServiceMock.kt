package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.key.DehydratedDeviceService
import kotlinx.coroutines.flow.MutableStateFlow

class DehydratedDeviceServiceMock : DehydratedDeviceService {
    override val pendingRehydration = MutableStateFlow(false)
}
