package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.megolm.MegolmMessage
import de.connect2x.trixnity.vodozemac.megolm.MegolmMessage.Text as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacMegolmMessage(val inner: Inner) : MegolmMessage {
    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close() = inner.close()
}
