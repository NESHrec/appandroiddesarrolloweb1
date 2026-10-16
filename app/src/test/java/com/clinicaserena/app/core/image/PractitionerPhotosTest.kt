package com.clinicaserena.app.core.image

import com.clinicaserena.app.core.image.PractitionerPhotoLoader.Companion.sampleSizeFor
import com.clinicaserena.app.core.network.NetworkModule
import com.clinicaserena.app.core.security.LogoutReason
import com.clinicaserena.app.core.security.SessionState
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class ResolvePhotoUrlTest {

    private val photo = "/api/v1/medicos/m1/foto?v=abc.png"

    @Test
    fun `resuelve contra el origen de cada build sin duplicar api v1`() {
        assertEquals(
            "http://10.0.2.2:8080/api/v1/medicos/m1/foto?v=abc.png",
            resolvePhotoUrl("http://10.0.2.2:8080/api/v1/", photo).toString(),
        )
        assertEquals(
            "http://localhost:8080/api/v1/medicos/m1/foto?v=abc.png",
            resolvePhotoUrl("http://localhost:8080/api/v1/", photo).toString(),
        )
        assertEquals(
            "https://api.clinica.example/api/v1/medicos/m1/foto?v=abc.png",
            resolvePhotoUrl("https://api.clinica.example/api/v1/", photo).toString(),
        )
    }

    @Test
    fun `rechaza vacios, otros hosts y esquemas`() {
        val base = "https://api.clinica.example/api/v1/"
        assertNull(resolvePhotoUrl(base, null))
        assertNull(resolvePhotoUrl(base, ""))
        assertNull(resolvePhotoUrl(base, "   "))
        assertNull(resolvePhotoUrl(base, "https://otro.example/foto.png"))
        assertNull(resolvePhotoUrl(base, "http://api.clinica.example/api/v1/medicos/m1/foto"))
        assertNull(resolvePhotoUrl(base, "//otro.example/foto.png"))
        assertNull(resolvePhotoUrl(base, "medicos/m1/foto"))
        assertNull(resolvePhotoUrl("no es una url", photo))
    }
}

class PractitionerInitialsTest {

    @Test
    fun `iniciales sin titulos`() {
        assertEquals("EM", practitionerInitials("Dra. Elena Morales"))
        assertEquals("MR", practitionerInitials("Dr. Mateo Rivera"))
        assertEquals("AG", practitionerInitials("  Ana   María  García "))
        assertEquals("ÁÑ", practitionerInitials("lic. álvaro ñuñez"))
        assertEquals("S", practitionerInitials("Sofía"))
        assertNull(practitionerInitials(""))
        assertNull(practitionerInitials("Dr."))
        assertNull(practitionerInitials("123"))
    }
}

/**
 * El cuerpo de prueba trae las dimensiones como texto («img:WxH»); el decodificador falso las lee y
 * cuenta cuántas veces se decodifica de verdad.
 */
private class FakeDecoder : PhotoDecoder<String> {
    var boundsCalls = 0
    val decodeCalls = mutableListOf<Int>()

    override fun bounds(bytes: ByteArray): ImageBounds? {
        boundsCalls++
        val match = Regex("img:(-?\\d+)x(-?\\d+)").find(String(bytes)) ?: return null
        return ImageBounds(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    override fun decode(bytes: ByteArray, sampleSize: Int): String {
        decodeCalls += sampleSize
        return "imagen(${String(bytes).take(20)}, muestra=$sampleSize)"
    }
}

class PractitionerPhotoLoaderTest {

    private val server = MockWebServer().apply { start() }
    private val decoder = FakeDecoder()
    private val baseUrl = server.url("/api/v1/").toString()
    private val loader = PractitionerPhotoLoader(baseUrl, NetworkModule.publicOkHttpClient(), decoder)

    @After
    fun tearDown() = server.close()

    private fun image(body: String = "img:256x256", type: String = "image/png", code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", type).body(body).build())

    @Test
    fun `descarga sin Authorization y decodifica`() = runTest {
        image()

        val result = loader.load("/api/v1/medicos/m1/foto?v=1.png")

        assertNotNull(result)
        val request = server.takeRequest()
        assertEquals("/api/v1/medicos/m1/foto?v=1.png", request.url.encodedPath + "?" + request.url.encodedQuery)
        assertNull("la foto es pública: nunca lleva Bearer", request.headers["Authorization"])
        assertEquals(listOf(1), decoder.decodeCalls)
    }

    @Test
    fun `404, tipo no permitido, cuerpo gigante o vacio devuelven null`() = runTest {
        image(body = """{"status":404,"code":"PHOTO_NOT_FOUND"}""", type = "application/json", code = 404)
        image(type = "text/html")
        image(type = "image/gif")
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "image/jpeg")
                .body(Buffer().write(ByteArray((PractitionerPhotoLoader.MAX_BYTES + 1).toInt()))).build(),
        )
        image(body = "")

        repeat(5) { assertNull(loader.load("/api/v1/medicos/m$it/foto?v=1")) }
        assertEquals(emptyList<Int>(), decoder.decodeCalls)
    }

    @Test
    fun `dimensiones exageradas o invalidas se rechazan antes de decodificar`() = runTest {
        image(body = "img:5000x64")
        image(body = "img:64x4097")
        image(body = "img:0x10")
        image(body = "sin dimensiones")

        repeat(4) { assertNull(loader.load("/api/v1/medicos/m$it/foto?v=1")) }
        assertEquals(4, decoder.boundsCalls)
        assertEquals("nunca se llamó a decode", emptyList<Int>(), decoder.decodeCalls)
    }

    @Test
    fun `una imagen grande se decodifica reducida`() = runTest {
        image(body = "img:3000x3000")

        assertNotNull(loader.load("/api/v1/medicos/m1/foto?v=1"))
        assertEquals(listOf(8), decoder.decodeCalls)
        assertEquals(1, sampleSizeFor(ImageBounds(256, 256), 256))
        assertEquals(2, sampleSizeFor(ImageBounds(600, 520), 256))
        assertEquals(16, sampleSizeFor(ImageBounds(4096, 4096), 256))
    }

    @Test
    fun `cache en memoria por URL completa y los fallos no se guardan`() = runTest {
        image()
        image(body = "nada", code = 404, type = "application/json")
        image()
        image()

        val first = loader.load("/api/v1/medicos/m1/foto?v=1")
        val second = loader.load("/api/v1/medicos/m1/foto?v=1")
        assertEquals(first, second)
        assertEquals("la segunda vez sale de la caché", 1, server.requestCount)
        assertEquals(first, loader.cached("/api/v1/medicos/m1/foto?v=1"))

        assertNull(loader.load("/api/v1/medicos/m2/foto?v=1"))
        assertNotNull("un fallo no se guarda: se reintenta", loader.load("/api/v1/medicos/m2/foto?v=1"))
        assertNotNull("otra versión (?v=) vuelve a descargar", loader.load("/api/v1/medicos/m1/foto?v=2"))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `descargas simultaneas de la misma foto se unen en una`() = runTest {
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "image/png").body("img:256x256")
                .headersDelay(300, TimeUnit.MILLISECONDS).build(),
        )

        val a = async { loader.load("/api/v1/medicos/m1/foto?v=1") }
        val b = async { loader.load("/api/v1/medicos/m1/foto?v=1") }

        assertNotNull(a.await())
        assertNotNull(b.await())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `se vacia al cerrar sesion y al vencer la sesion`() = runTest {
        val session = Session("token", AccountType.PACIENTE, Role.PACIENTE, Instant.EPOCH, "p1", "p@ejemplo.invalid")
        val states = MutableStateFlow<SessionState>(SessionState.Active(session))
        loader.bindToSession(states, backgroundScope)
        runCurrent()
        repeat(3) { image() }

        loader.load("/api/v1/medicos/m1/foto?v=1")
        assertNotNull(loader.cached("/api/v1/medicos/m1/foto?v=1"))

        states.value = SessionState.ReauthRequired(session)
        runCurrent()
        assertNull("sesión vencida: caché vacía", loader.cached("/api/v1/medicos/m1/foto?v=1"))

        states.value = SessionState.Active(session)
        runCurrent()
        loader.load("/api/v1/medicos/m1/foto?v=1")
        assertNotNull(loader.cached("/api/v1/medicos/m1/foto?v=1"))

        states.value = SessionState.LoggedOut(LogoutReason.NONE)
        runCurrent()
        assertNull("logout: caché vacía", loader.cached("/api/v1/medicos/m1/foto?v=1"))

        loader.load("/api/v1/medicos/m1/foto?v=1")
        assertEquals("tras limpiar, vuelve al servidor", 3, server.requestCount)
    }
}
