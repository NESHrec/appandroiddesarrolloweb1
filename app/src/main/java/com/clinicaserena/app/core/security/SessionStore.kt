package com.clinicaserena.app.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64

/**
 * Guarda la sesión cifrada en DataStore. Todo el registro (token, tipo de cuenta, rol y vencimiento)
 * se cifra con [TokenCipher]; en disco solo quedan IV y texto cifrado en Base64.
 *
 * Si el registro no se puede descifrar (clave del Keystore invalidada, respaldo restaurado de otro
 * dispositivo, datos dañados) se borran el registro y la clave y se devuelve `null`: la app pide
 * iniciar sesión en lugar de fallar.
 */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val json: Json = Json,
) {

    suspend fun save(session: Session) {
        val record = StoredSession(
            token = session.token,
            accountType = session.accountType.name,
            role = session.role.name,
            expiresAtEpochSeconds = session.expiresAt.epochSecond,
        )
        val payload = cipher.encrypt(json.encodeToString(StoredSession.serializer(), record).toByteArray())
        dataStore.edit { prefs ->
            prefs[KEY_IV] = encoder.encodeToString(payload.iv)
            prefs[KEY_DATA] = encoder.encodeToString(payload.ciphertext)
        }
    }

    suspend fun load(): Session? = try {
        val prefs = dataStore.data.first()
        val iv = prefs[KEY_IV]
        val data = prefs[KEY_DATA]
        if (iv == null && data == null) {
            null
        } else {
            requireNotNull(iv) { "Registro incompleto" }
            requireNotNull(data) { "Registro incompleto" }
            val plaintext = cipher.decrypt(EncryptedPayload(decoder.decode(iv), decoder.decode(data)))
            json.decodeFromString(StoredSession.serializer(), plaintext.decodeToString()).toSession()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        wipe()
        null
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun wipe() {
        try {
            clear()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Si DataStore no se puede escribir, al menos se elimina la clave: el dato queda ilegible.
        }
        try {
            cipher.deleteKey()
        } catch (_: Exception) {
            // Sin clave que borrar o Keystore no disponible: no hay nada más que hacer.
        }
    }

    private fun StoredSession.toSession(): Session = Session(
        token = token,
        accountType = AccountType.valueOf(accountType),
        role = Role.valueOf(role),
        expiresAt = Instant.ofEpochSecond(expiresAtEpochSeconds),
    )

    @Serializable
    private data class StoredSession(
        val token: String,
        val accountType: String,
        val role: String,
        val expiresAtEpochSeconds: Long,
    )

    private companion object {
        val KEY_IV = stringPreferencesKey("session_iv")
        val KEY_DATA = stringPreferencesKey("session_data")
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
