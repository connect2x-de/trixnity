package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.olm.PreKeyMessageFactory
import de.connect2x.trixnity.crypto.driver.vodozemac.rethrow
import de.connect2x.trixnity.vodozemac.olm.OlmMessage

object VodozemacPreKeyMessageFactory : PreKeyMessageFactory {
    override fun invoke(bytes: ByteArray): VodozemacPreKeyMessage = rethrow {
        VodozemacPreKeyMessage(OlmMessage.PreKey.Text(bytes))
    }

    override fun invoke(base64: String): VodozemacPreKeyMessage = rethrow {
        VodozemacPreKeyMessage(OlmMessage.PreKey.Text(base64))
    }
}
