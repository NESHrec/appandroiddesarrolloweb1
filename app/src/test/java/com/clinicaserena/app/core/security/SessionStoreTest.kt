package com.clinicaserena.app.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class SessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cipher = FakeTokenCipher()
    private val store by lazy {
        SessionStore(
            PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("session.preferences_pb") },
            cipher,
        )
    }

    private val session = Session(
        token = "token-abc",
        accountType = AccountType.PERSONAL,
        role = Role.MEDICO,
        expiresAt = Instant.parse("2026-10-07T07:00:00Z"),
        subjectId = "cuenta-1",
        email = "medico@ejemplo.invalid",
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `guarda y recupera la sesion`() = runBlocking {
        store.save(session)

        assertEquals(session, store.load())
    }

    @Test
    fun `sin datos devuelve null`() = runBlocking {
        assertNull(store.load())
    }

    @Test
    fun `si no se puede descifrar borra la sesion y la clave sin fallar`() = runBlocking {
        store.save(session)
        cipher.failDecrypt = true

        assertNull(store.load())
        assertEquals(1, cipher.deletedKeys)

        cipher.failDecrypt = false
        assertNull("el registro ilegible debe quedar borrado", store.load())
    }

    @Test
    fun `clear borra la sesion`() = runBlocking {
        store.save(session)
        store.clear()

        assertNull(store.load())
    }
}
