package com.clinicaserena.app.core.security

import javax.crypto.AEADBadTagException

/** Cifrado falso para pruebas en JVM: invierte los bytes. Puede simular una clave invalidada. */
class FakeTokenCipher : TokenCipher {
    var failDecrypt = false
    var deletedKeys = 0
        private set

    override fun encrypt(plaintext: ByteArray) = EncryptedPayload(byteArrayOf(1, 2, 3), plaintext.reversedArray())

    override fun decrypt(payload: EncryptedPayload): ByteArray {
        if (failDecrypt) throw AEADBadTagException("clave invalidada")
        return payload.ciphertext.reversedArray()
    }

    override fun deleteKey() {
        deletedKeys++
    }
}
