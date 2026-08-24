package de.connect2x.trixnity.libolm

import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OlmPkEncryptionTest {

    @Test
    fun encrypt() = runTest {
        freeAfter(OlmPkDecryption.create()) { pkDecryption ->
            val key = pkDecryption.publicKey
            freeAfter(OlmPkEncryption.create(key)) { pkEncryption ->
                val message = pkEncryption.encrypt("Public key test")
                message.ephemeralKey shouldNot beBlank()
                message.mac shouldNot beBlank()
                message.cipherText shouldNot beBlank()
            }
        }
    }
}
