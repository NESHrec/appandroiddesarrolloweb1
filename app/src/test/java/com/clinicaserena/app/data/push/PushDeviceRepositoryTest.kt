package com.clinicaserena.app.data.push

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.ApiTestRig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PushDeviceRepositoryTest {

    private val rig = ApiTestRig()
    private val repository = RemotePushDeviceRepository(rig.retrofit.create(PushDeviceApi::class.java), rig.apiCaller)

    @After
    fun tearDown() = rig.close()

    @Test
    fun `registrar usa PUT con el contrato exacto y el Bearer`() = runTest {
        rig.enqueue(204)

        assertEquals(ApiResult.Success(Unit), repository.register(FCM_TOKEN))

        val request = rig.server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/pacientes/me/dispositivos-push", request.url.encodedPath)
        assertEquals("""{"token":"$FCM_TOKEN","platform":"ANDROID"}""", request.body?.utf8())
        assertEquals("Bearer ${ApiTestRig.TOKEN}", request.headers["Authorization"])
    }

    @Test
    fun `retirar usa POST con el token en el cuerpo y nunca en la URL`() = runTest {
        rig.enqueue(204)

        assertEquals(ApiResult.Success(Unit), repository.remove(FCM_TOKEN))

        val request = rig.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/pacientes/me/dispositivos-push/retiro", request.url.encodedPath)
        assertEquals(null, request.url.query)
        assertEquals("""{"token":"$FCM_TOKEN"}""", request.body?.utf8())
    }

    @Test
    fun `los codigos del backend se conservan tipados`() = runTest {
        rig.enqueue(409, ApiTestRig.error(409, "PUSH_NOT_ENABLED"))
        rig.enqueue(400, """{"status":400,"code":"PUSH_TOKEN_NOT_SIMULATED","message":"Mensaje","fieldErrors":[]}""")
        rig.enqueue(403, ApiTestRig.error(403, "FORBIDDEN"))

        assertEquals(ApiResult.Conflict("PUSH_NOT_ENABLED", "Mensaje"), repository.register(FCM_TOKEN))
        assertEquals(ApiResult.BadRequest(400, "PUSH_TOKEN_NOT_SIMULATED", "Mensaje"), repository.register(FCM_TOKEN))
        assertEquals(ApiResult.Forbidden("FORBIDDEN", "Mensaje"), repository.register(FCM_TOKEN))
    }

    @Test
    fun `401 al registrar abre el re-login, 401 al retirar no`() = runTest {
        rig.enqueue(401, ApiTestRig.error(401, "UNAUTHENTICATED"))
        rig.enqueue(401, ApiTestRig.error(401, "UNAUTHENTICATED"))

        repository.register(FCM_TOKEN)
        assertEquals(1, rig.expiredCount)

        repository.remove(FCM_TOKEN)
        assertEquals(1, rig.expiredCount)
    }

    @Test
    fun `el token no aparece en los logs HTTP ni en toString`() = runTest {
        rig.enqueue(204)
        rig.enqueue(204)

        repository.register(FCM_TOKEN)
        repository.remove(FCM_TOKEN)

        assertFalse(rig.logLines.any { FCM_TOKEN in it })
        assertFalse(FCM_TOKEN in RegisterPushDeviceRequest(FCM_TOKEN, "ANDROID").toString())
        assertFalse(FCM_TOKEN in RemovePushDeviceRequest(FCM_TOKEN).toString())
    }

    private companion object {
        const val FCM_TOKEN = "fcm-token-de-prueba:APA91b_abcdefghijklmnop"
    }
}
