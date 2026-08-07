package de.connect2x.trixnity.client.cryptodriver

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionService
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionServiceImpl
import de.connect2x.trixnity.crypto.olm.MegolmEncryptionServiceRequestHandler
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmEncryptionServiceImpl
import de.connect2x.trixnity.crypto.olm.OlmEncryptionServiceRequestHandler
import de.connect2x.trixnity.crypto.olm.OlmEventHandler
import de.connect2x.trixnity.crypto.olm.OlmEventHandlerImpl
import de.connect2x.trixnity.crypto.olm.OlmEventHandlerRequestHandler
import de.connect2x.trixnity.crypto.olm.OlmKeysChangeEmitter
import de.connect2x.trixnity.crypto.olm.OlmStore
import de.connect2x.trixnity.crypto.sign.SignService
import de.connect2x.trixnity.crypto.sign.SignServiceImpl
import de.connect2x.trixnity.crypto.sign.SignServiceStore
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun createCryptoModule() = module {
    singleOf(::ClientOlmKeysChangeEmitter) { bind<OlmKeysChangeEmitter>() }
    singleOf(::ClientSignServiceStore) { bind<SignServiceStore>() }
    singleOf(::SignServiceImpl) { bind<SignService>() }
    singleOf(::ClientOlmEventHandlerRequestHandler) { bind<OlmEventHandlerRequestHandler>() }
    singleOf(::ClientOlmEncryptionServiceRequestHandler) { bind<OlmEncryptionServiceRequestHandler>() }
    singleOf(::ClientMegolmEncryptionServiceRequestHandler) { bind<MegolmEncryptionServiceRequestHandler>() }
    singleOf(::ClientOlmStore) { bind<OlmStore>() }
    singleOf(::OlmEncryptionServiceImpl) { bind<OlmEncryptionService>() }
    singleOf(::MegolmEncryptionServiceImpl) { bind<MegolmEncryptionService>() }
    single<OlmEventHandler> {
        OlmEventHandlerImpl(
            userInfo = get(),
            eventEmitter = get<MatrixClientServerApiClient>().sync,
            olmKeysChangeEmitter = get(),
            signService = get(),
            olmEncryptionService = get(),
            requestHandler = get(),
            store = get(),
            clock = get(),
            driver = get()
        )
    }.apply {
        bind<EventHandler>()
        named<OlmEventHandler>()
    }
}
