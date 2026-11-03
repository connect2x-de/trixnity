package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.megolm.MegolmMessageFactory
import de.connect2x.trixnity.crypto.driver.vodozemac.rethrow
import de.connect2x.trixnity.vodozemac.megolm.MegolmMessage

object VodozemacMegolmMessageFactory : MegolmMessageFactory {
    override fun invoke(bytes: ByteArray): VodozemacMegolmMessage = rethrow {
        VodozemacMegolmMessage(MegolmMessage.Text(bytes))
    }

    override fun invoke(base64: String): VodozemacMegolmMessage = rethrow {
        VodozemacMegolmMessage(MegolmMessage.Text(base64))
    }
}
