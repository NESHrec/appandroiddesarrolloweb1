package com.clinicaserena.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray)

/** Cifra la sesión antes de guardarla. Abstraído para probar el almacén sin Android Keystore. */
interface TokenCipher {
    fun encrypt(plaintext: ByteArray): EncryptedPayload

    /** Lanza [GeneralSecurityException] si la clave no existe, fue invalidada o el dato no corresponde. */
    fun decrypt(payload: EncryptedPayload): ByteArray

    fun deleteKey()
}

/**
 * AES-256-GCM con la clave en Android Keystore: la clave no sale del almacén seguro del sistema
 * ni se incluye en respaldos. Sustituye a EncryptedSharedPreferences (deprecado).
 */
class KeystoreTokenCipher(private val alias: String = DEFAULT_ALIAS) : TokenCipher {

    override fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: generateKey())
        return EncryptedPayload(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(payload: EncryptedPayload): ByteArray {
        val key = existingKey() ?: throw GeneralSecurityException("No existe la clave de sesión")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, payload.iv))
        return cipher.doFinal(payload.ciphertext)
    }

    override fun deleteKey() {
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "clinica_serena_session_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128
    }
}
