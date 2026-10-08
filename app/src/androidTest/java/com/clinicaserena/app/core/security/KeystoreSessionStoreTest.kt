package com.clinicaserena.app.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** Cifrado real con Android Keystore. Usa un alias y un archivo propios para no tocar la sesión de la app. */
@RunWith(AndroidJUnit4::class)
class KeystoreSessionStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cipher = KeystoreTokenCipher(alias = "clinica_serena_test_key")
    private val file = File(context.filesDir, "datastore/test_session.preferences_pb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = SessionStore(PreferenceDataStoreFactory.create(scope = scope) { file }, cipher)
    private val token = "token-en-claro-que-no-debe-aparecer"

    @After
    fun tearDown() {
        scope.cancel()
        cipher.deleteKey()
        file.delete()
    }

    @Test
    fun cifraYDescifraConKeystore() {
        val plaintext = "dato de prueba".toByteArray()
        val payload = cipher.encrypt(plaintext)

        assertFalse(payload.ciphertext.contentEquals(plaintext))
        assertArrayEquals(plaintext, cipher.decrypt(payload))
    }

    @Test
    fun elTokenNoQuedaEnTextoPlanoEnDisco() = runBlocking {
        val session = Session(token, AccountType.PACIENTE, Role.PACIENTE, Instant.parse("2026-10-07T07:00:00Z"), "paciente-1", "paciente@ejemplo.invalid")
        store.save(session)

        val bytes = file.readBytes().decodeToString()
        assertFalse(bytes.contains(token))
        assertFalse(bytes.contains("PACIENTE"))
        assertFalse(bytes.contains("paciente@ejemplo.invalid"))
        assertEquals(session, store.load())
    }

    @Test
    fun siSeBorraLaClaveLaSesionSeDescartaSinFallar() = runBlocking {
        store.save(Session(token, AccountType.PERSONAL, Role.MEDICO, Instant.parse("2026-10-07T07:00:00Z"), "cuenta-1", "medico@ejemplo.invalid"))
        cipher.deleteKey()

        assertNull(store.load())
        assertNull(store.load())
    }
}
