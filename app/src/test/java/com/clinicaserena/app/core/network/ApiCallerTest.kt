package com.clinicaserena.app.core.network

import com.clinicaserena.app.data.auth.AuthApi
import com.clinicaserena.app.data.auth.RemoteAuthRepository
import com.clinicaserena.app.data.auth.UnifiedLogin
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

class ApiCallerTest {

    private interface TestApi {
        @GET("medico/citas")
        suspend fun citas(@Query("from") from: String): Response<List<String>>

        @GET("privado")
        suspend fun privado(): Response<Map<String, String>>

        @POST("auth/logout")
        suspend fun logout(): Response<Unit>
    }

    private val rig = ApiTestRig()
    private val authRepository = RemoteAuthRepository(rig.retrofit.create(AuthApi::class.java), rig.apiCaller)
    private val testApi = rig.retrofit.create(TestApi::class.java)

    @After
    fun tearDown() = rig.close()

    @Test
    fun `login con credenciales incorrectas no vence la sesion`() = runTest {
        rig.enqueue(401, ApiTestRig.error(401, "AUTHENTICATION_FAILED"))

        val result = authRepository.loginUnified("paciente@ejemplo.invalid", "clave-incorrecta")

        assertEquals(ApiResult.InvalidCredentials("Mensaje"), result)
        assertEquals(0, rig.expiredCount)
    }

    @Test
    fun `login exitoso se traduce a dominio`() = runTest {
        rig.enqueue(
            200,
            """{"accessToken":"abc","tokenType":"Bearer","expiresInSeconds":1800,"accountType":"PERSONAL","role":"MEDICO"}""",
        )

        val result = authRepository.loginUnified("medico@ejemplo.invalid", "clave-de-prueba-12")

        assertEquals(ApiResult.Success(UnifiedLogin("abc", AccountType.PERSONAL, Role.MEDICO, 1800)), result)
    }

    @Test
    fun `401 UNAUTHENTICATED en peticion autenticada vence la sesion`() = runTest {
        rig.enqueue(401, ApiTestRig.error(401, "UNAUTHENTICATED"))

        val result = authRepository.currentRole(AccountType.PACIENTE)

        assertTrue(result is ApiResult.Unauthenticated)
        assertEquals(1, rig.expiredCount)
    }

    @Test
    fun `401 en endpoint publico no vence la sesion`() = runTest {
        rig.enqueue(401, ApiTestRig.error(401, "UNAUTHENTICATED"))

        val result = authRepository.loginUnified("usuario@ejemplo.invalid", "x")

        assertTrue(result is ApiResult.Unauthenticated)
        assertEquals(0, rig.expiredCount)
    }

    @Test
    fun `403 y 409 no se convierten en error generico`() = runTest {
        rig.enqueue(403, ApiTestRig.error(403, "PRACTITIONER_LINK_REQUIRED"))
        rig.enqueue(409, ApiTestRig.error(409, "SLOT_NOT_AVAILABLE"))

        assertEquals(
            ApiResult.Forbidden("PRACTITIONER_LINK_REQUIRED", "Mensaje"),
            rig.apiCaller.call(authenticated = true) { testApi.privado() },
        )
        assertEquals(
            ApiResult.Conflict("SLOT_NOT_AVAILABLE", "Mensaje"),
            rig.apiCaller.call(authenticated = true) { testApi.privado() },
        )
        assertEquals(0, rig.expiredCount)
    }

    @Test
    fun `logout 204 sin cuerpo es exito`() = runTest {
        rig.enqueue(204)
        rig.enqueue(204)

        assertEquals(ApiResult.Success(Unit), rig.apiCaller.callNoContent(authenticated = true) { testApi.logout() })
        assertEquals(ApiResult.Success(Unit), authRepository.logout(AccountType.PERSONAL))
        assertEquals("/api/v1/staff/auth/logout", rig.server.let { it.takeRequest(); it.takeRequest() }.url.encodedPath)
    }

    @Test
    fun `204 donde se espera cuerpo es Unexpected`() = runTest {
        rig.enqueue(204)

        assertEquals(ApiResult.Unexpected(204), rig.apiCaller.call(authenticated = true) { testApi.privado() })
    }

    @Test
    fun `cuerpo exitoso que no cumple el contrato es Unexpected`() = runTest {
        rig.enqueue(200, """{"status": 5""")

        assertEquals(ApiResult.Unexpected(null), rig.apiCaller.call(authenticated = true) { testApi.privado() })
    }

    @Test
    fun `servidor inalcanzable es NetworkError`() = runTest {
        rig.server.close()

        assertEquals(ApiResult.NetworkError, rig.apiCaller.call(authenticated = true) { testApi.privado() })
    }

    @Test
    fun `el Bearer se adjunta solo a endpoints no publicos`() = runTest {
        rig.enqueue(200, "{}")
        rig.enqueue(401, ApiTestRig.error(401, "AUTHENTICATION_FAILED"))

        rig.apiCaller.call(authenticated = true) { testApi.privado() }
        authRepository.loginUnified("usuario@ejemplo.invalid", "x")

        assertEquals("Bearer ${ApiTestRig.TOKEN}", rig.server.takeRequest().headers["Authorization"])
        assertNull(rig.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `sin token no se envia Authorization`() = runTest {
        rig.token = null
        rig.enqueue(200, "{}")

        rig.apiCaller.call(authenticated = true) { testApi.privado() }

        assertNull(rig.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `el signo mas de un offset se codifica en el query`() = runTest {
        rig.enqueue(200, "[]")

        rig.apiCaller.call(authenticated = true) { testApi.citas("2026-10-07T00:00:00+02:00") }

        val url = rig.server.takeRequest().url
        assertTrue(url.encodedQuery!!.contains("%2B02"))
        assertEquals("2026-10-07T00:00:00+02:00", url.queryParameter("from"))
    }

    @Test
    fun `el log HTTP no contiene Bearer, contrasenas ni cuerpos`() = runTest {
        rig.enqueue(200, """{"accessToken":"token-de-respuesta","tokenType":"Bearer","expiresInSeconds":1800,"accountType":"PACIENTE","role":"PACIENTE"}""")
        rig.enqueue(200, """{"diagnosis":"dato-clinico"}""")

        authRepository.loginUnified("paciente@ejemplo.invalid", "contrasena-secreta")
        rig.apiCaller.call(authenticated = true) { testApi.privado() }

        val log = rig.logLines.joinToString("\n")
        assertTrue("debe registrar al menos la línea de la petición", log.contains("/api/v1/privado"))
        assertFalse(log.contains(ApiTestRig.TOKEN))
        assertFalse(log.contains("contrasena-secreta"))
        assertFalse(log.contains("token-de-respuesta"))
        assertFalse(log.contains("dato-clinico"))
    }
}
