package de.connect2x.trixnity.client.key

import de.connect2x.trixnity.client.user.LazyMemberEventHandler
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC3814
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun createKeyModule() = module {
    singleOf(::OutdatedKeysHandler) {
        bind<EventHandler>()
        bind<LazyMemberEventHandler>()
        named<OutdatedKeysHandler>()
    }
    singleOf(::IncomingRoomKeyRequestEventHandler) {
        bind<EventHandler>()
        named<IncomingRoomKeyRequestEventHandler>()
    }
    singleOf(::OutgoingRoomKeyRequestEventHandlerImpl) {
        bind<OutgoingRoomKeyRequestEventHandler>()
        bind<EventHandler>()
        named<OutgoingRoomKeyRequestEventHandler>()
    }
    singleOf(::IncomingSecretKeyRequestEventHandler) {
        bind<EventHandler>()
        named<IncomingSecretKeyRequestEventHandler>()
    }
    single<EventHandler>(named<OutgoingSecretKeyRequestEventHandler>()) {
        OutgoingSecretKeyRequestEventHandler(
            userInfo = get(),
            api = get(),
            olmEventHandler = get(),
            keyBackupService = get(named<KeyBackupService>()),
            keyStore = get(),
            globalAccountDataStore = get(),
            currentSyncState = get(),
            clock = get(),
            driver = get(),
            tm = get(),
        )
    }
    singleOf(::KeySecretServiceImpl) { bind<KeySecretService>() }
    singleOf(::KeyTrustServiceImpl) { bind<KeyTrustService>() }
    singleOf(::KeyBackupServiceImpl) {
        bind<KeyBackupService>()
        bind<EventHandler>()
        named<KeyBackupService>()
    }
    single<KeyService> {
        KeyServiceImpl(
            userInfo = get(),
            accountStore = get(),
            keyStore = get(),
            olmCryptoStore = get(),
            globalAccountDataStore = get(),
            tm = get(),
            roomService = get(),
            signService = get(),
            keyTrustService = get(),
            api = get(),
            matrixClientConfiguration = get(),
            driver = get(),
        )
    }

    @OptIn(MSC3814::class)
    singleOf(::DehydratedDeviceServiceImpl) {
        bind<EventHandler>()
        named<DehydratedDeviceService>()
        bind<DehydratedDeviceService>()
    }
    single<KeyShareServiceImpl> {
            KeyShareServiceImpl(
                accountStore = get(),
                olmCryptoStore = get(),
                keyStore = get(),
                keyBackupService = get(named<KeyBackupService>()),
                dehydratedDeviceService = get(named<DehydratedDeviceService>()),
                olmEncryptionService = get(),
                mediaService = get(),
                api = get(),
                currentSyncState = get(),
                cryptoDriver = get(),
                json = get(),
                userInfo = get(),
            )
        }
        .apply {
            bind<EventHandler>()
            named<KeyShareService>()
            bind<KeyShareService>()
        }
}
