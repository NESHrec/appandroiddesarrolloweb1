package com.clinicaserena.app.core.image

import com.clinicaserena.app.core.security.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `photoUrl` de `PractitionerDto` es una ruta del servidor que ya incluye `/api/v1`
 * (`/api/v1/medicos/{id}/foto?v=…`). Se resuelve contra el ORIGEN de la URL base de la API de cada build
 * (debug/qaLocal `http://10.0.2.2:8080` o `localhost`, release `https://…`), sin duplicar `/api/v1`.
 * Solo se aceptan rutas del mismo servidor: nunca se descarga de otro host ni con otro esquema.
 */
fun resolvePhotoUrl(apiBaseUrl: String, photoUrl: String?): HttpUrl? {
    if (photoUrl.isNullOrBlank() || !photoUrl.startsWith("/") || photoUrl.startsWith("//")) return null
    val base = apiBaseUrl.toHttpUrlOrNull() ?: return null
    val resolved = base.resolve(photoUrl) ?: return null
    return resolved.takeIf { it.scheme == base.scheme && it.host == base.host && it.port == base.port }
}

/** Iniciales para el respaldo sin foto, sin títulos («Dra. Elena Morales» → «EM»). `null` si no queda texto. */
fun practitionerInitials(fullName: String): String? {
    val words = fullName.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it.trimEnd('.').lowercase() !in TITLES }
        .filter { it.first().isLetter() }
    if (words.isEmpty()) return null
    val initials = if (words.size == 1) words.first().take(1) else words.first().take(1) + words.last().take(1)
    return initials.uppercase()
}

private val TITLES = setOf("dr", "dra", "lic", "licda", "ing", "inga")

data class ImageBounds(val width: Int, val height: Int)

/** Lectura de dimensiones (sin decodificar) y decodificación reducida. En Android usa `BitmapFactory`. */
interface PhotoDecoder<T : Any> {
    fun bounds(bytes: ByteArray): ImageBounds?
    fun decode(bytes: ByteArray, sampleSize: Int): T?
}

/**
 * Fotos públicas de profesionales (no clínicas). Usa un [OkHttpClient] propio SIN el interceptor de
 * autenticación: la foto es pública y el Bearer nunca debe viajar en esta petición.
 *
 * Caché solo en memoria (LRU por URL completa; el `?v=` del servidor cambia al reemplazar la foto). Sin
 * disco: Spring responde `no-store`. Los fallos no se guardan, y la caché se vacía al cerrar o vencer la
 * sesión ([bindToSession]).
 */
class PractitionerPhotoLoader<T : Any>(
    private val apiBaseUrl: String,
    private val client: OkHttpClient,
    private val decoder: PhotoDecoder<T>,
    private val targetPx: Int = DEFAULT_TARGET_PX,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, T>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = size > maxEntries
    }
    private val inFlight = mutableMapOf<String, Deferred<T?>>()
    private val inFlightMutex = Mutex()

    /** Generación de la caché: un resultado que llega después de [clear] no se guarda. */
    @Volatile
    private var generation = 0

    /** Imagen ya en memoria, sin red (para mostrarla de inmediato al recomponer). */
    fun cached(photoUrl: String?): T? {
        val key = resolvePhotoUrl(apiBaseUrl, photoUrl)?.toString() ?: return null
        return synchronized(lock) { cache[key] }
    }

    /** Imagen o `null` (sin foto, 404, red, tipo, tamaño o dimensiones no válidos): se muestran las iniciales. */
    suspend fun load(photoUrl: String?): T? {
        val url = resolvePhotoUrl(apiBaseUrl, photoUrl) ?: return null
        val key = url.toString()
        synchronized(lock) { cache[key] }?.let { return it }
        val startedAt = generation
        // Descargas simultáneas de la misma URL se unen en una sola.
        val job = inFlightMutex.withLock {
            inFlight[key] ?: scope.async { fetch(url) }.also { inFlight[key] = it }
        }
        val image = try {
            job.await()
        } finally {
            inFlightMutex.withLock { if (inFlight[key] === job) inFlight.remove(key) }
        }
        if (image != null) synchronized(lock) { if (generation == startedAt) cache[key] = image }
        return image
    }

    /** Vacía la memoria (logout o sesión vencida). */
    fun clear() {
        synchronized(lock) {
            generation++
            cache.clear()
        }
    }

    /** Limpia al cerrar sesión (cualquier motivo) y al vencer la sesión, igual que el resto del estado en memoria. */
    fun bindToSession(states: Flow<SessionState>, scope: CoroutineScope) {
        scope.launch {
            states.collect { state ->
                if (state is SessionState.LoggedOut || state is SessionState.ReauthRequired) clear()
            }
        }
    }

    private fun fetch(url: HttpUrl): T? = try {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.code != 200) return null
            val type = response.body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }
            if (type !in ALLOWED_TYPES) return null
            if (response.body.contentLength() > MAX_BYTES) return null
            val source = response.body.source()
            // Corta la lectura si el cuerpo supera el límite, aunque no declare Content-Length.
            if (source.request(MAX_BYTES + 1)) return null
            val bytes = source.readByteArray()
            val bounds = decoder.bounds(bytes) ?: return null
            if (!bounds.isAcceptable()) return null
            decoder.decode(bytes, sampleSizeFor(bounds, targetPx))
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Igual al máximo por defecto del backend (`PRACTITIONER_PHOTO_MAX_BYTES`). */
        const val MAX_BYTES = 2L * 1024 * 1024
        /** Más grande que esto no es una foto de perfil razonable: se rechaza antes de decodificar. */
        const val MAX_DIMENSION = 4096
        const val DEFAULT_TARGET_PX = 256
        const val DEFAULT_MAX_ENTRIES = 16
        private val ALLOWED_TYPES = setOf("image/jpeg", "image/png")

        fun ImageBounds.isAcceptable(): Boolean = width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION

        /** Mayor potencia de 2 que deja ambos lados en al menos [targetPx]. */
        fun sampleSizeFor(bounds: ImageBounds, targetPx: Int): Int {
            var sample = 1
            while (bounds.width / (sample * 2) >= targetPx && bounds.height / (sample * 2) >= targetPx) sample *= 2
            return sample
        }
    }
}
