package de.connect2x.trixnity.client.rtc

import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

@MSC4143
@OptIn(MSC4354::class)
fun createRtcModule() = module {
    single { RtcServiceImpl(roomStateStore = get(), stickyEventStore = get(), keyService = get()) }
        .apply {
            bind<RtcService>()
            bind<EventHandler>()
            named<RtcService>()
        }
}
