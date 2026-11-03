package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import de.connect2x.trixnity.crypto.driver.libolm.rethrow
import de.connect2x.trixnity.crypto.driver.megolm.ExportedSessionKey
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSessionFactory
import de.connect2x.trixnity.crypto.driver.megolm.SessionKey
import de.connect2x.trixnity.libolm.OlmInboundGroupSession

object LibOlmInboundGroupSessionFactory : InboundGroupSessionFactory {

    override fun invoke(sessionKey: SessionKey): LibOlmInboundGroupSession {
        require(sessionKey is LibOlmSessionKey)

        return rethrow { LibOlmInboundGroupSession(OlmInboundGroupSession.create(sessionKey.inner)) }
    }

    override fun import(sessionKey: ExportedSessionKey): LibOlmInboundGroupSession {
        require(sessionKey is LibOlmExportedSessionKey)

        return rethrow { LibOlmInboundGroupSession(OlmInboundGroupSession.import(sessionKey.inner)) }
    }

    override fun fromPickle(pickle: String, pickleKey: PickleKey?): LibOlmInboundGroupSession {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return rethrow { LibOlmInboundGroupSession(OlmInboundGroupSession.unpickle(pickleKey?.inner, pickle)) }
    }
}
