package de.connect2x.trixnity.vodozemac.pkencryption

import de.connect2x.trixnity.vodozemac.Curve25519PublicKey
import de.connect2x.trixnity.vodozemac.VodozemacException
import de.connect2x.trixnity.vodozemac.bindings.pkencryption.PkEncryptionBindings
import de.connect2x.trixnity.vodozemac.toByteArray
import de.connect2x.trixnity.vodozemac.utils.*

class PkEncryption internal constructor(ptr: NativePointer) : Managed(ptr, PkEncryptionBindings::free) {

    private fun <T : PkEncryptionMessage> encryptRaw(
        bytes: ByteArray,
        construct: (ByteArray, ByteArray, Curve25519PublicKey) -> T,
    ): T = managedReachableScope {
        val result =
            withResult(NativePointerArray(6)) { PkEncryptionBindings.encrypt(it, ptr, bytes.toInterop(), bytes.size) }

        if (result[0].intValue != 0)
            throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

        val ciphertext = result[1].toByteArray(result[2].intValue)
        val mac = result[3].toByteArray(result[4].intValue)
        val ephemeralKey = Curve25519PublicKey(result[5])

        construct(ciphertext, mac, ephemeralKey)
    }

    fun encrypt(plaintext: ByteArray): PkEncryptionMessage.Bytes = encryptRaw(plaintext, PkEncryptionMessage::Bytes)

    fun encrypt(plaintext: String): PkEncryptionMessage.Text =
        encryptRaw(plaintext.encodeToByteArray(), PkEncryptionMessage::Text)

    companion object {
        operator fun invoke(publicKey: Curve25519PublicKey): PkEncryption =
            PkEncryption(PkEncryptionBindings.fromKey(publicKey.ptr))
    }
}
