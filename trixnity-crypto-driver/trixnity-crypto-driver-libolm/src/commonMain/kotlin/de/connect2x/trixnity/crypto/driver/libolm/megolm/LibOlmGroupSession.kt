package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import de.connect2x.trixnity.crypto.driver.libolm.rethrow
import de.connect2x.trixnity.crypto.driver.megolm.GroupSession
import de.connect2x.trixnity.crypto.driver.megolm.SessionKey
import de.connect2x.trixnity.libolm.OlmOutboundGroupSession
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmGroupSession(private val inner: OlmOutboundGroupSession) : GroupSession {
    override val sessionId: String
        get() = rethrow { inner.sessionId }

    override val messageIndex: Int
        get() = rethrow { inner.messageIndex.toInt() }

    override val sessionKey: SessionKey
        get() = rethrow { LibOlmSessionKey(inner.sessionKey) }

    override fun encrypt(plaintext: String): LibOlmMegolmMessage = rethrow {
        LibOlmMegolmMessage(inner.encrypt(plaintext))
    }

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return rethrow { inner.pickle(pickleKey?.inner) }
    }

    override fun close() = inner.free()
}
