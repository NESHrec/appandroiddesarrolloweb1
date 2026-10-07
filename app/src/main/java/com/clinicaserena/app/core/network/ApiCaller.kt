package com.clinicaserena.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

/**
 * Ejecuta llamadas Retrofit y las traduce a [ApiResult].
 *
 * Solo un 401 `UNAUTHENTICATED` en una petición autenticada avisa a [onSessionExpired]. Un 401
 * `AUTHENTICATION_FAILED` del login es "credenciales incorrectas" y no toca la sesión.
 */
class ApiCaller(
    private val errorMapper: ErrorMapper,
    private val onSessionExpired: () -> Unit,
) {

    suspend fun <T> call(authenticated: Boolean, request: suspend () -> Response<T>): ApiResult<T> =
        execute(authenticated, request) { status -> ApiResult.Unexpected(status) }

    /** Para respuestas sin cuerpo (204 de logout): Retrofit entrega `body() == null` en un 204. */
    suspend fun callNoContent(authenticated: Boolean, request: suspend () -> Response<Unit>): ApiResult<Unit> =
        execute(authenticated, request) { ApiResult.Success(Unit) }

    private suspend fun <T> execute(
        authenticated: Boolean,
        request: suspend () -> Response<T>,
        onEmptyBody: (status: Int) -> ApiResult<T>,
    ): ApiResult<T> {
        val result: ApiResult<T> = try {
            val response = request()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) ApiResult.Success(body) else onEmptyBody(response.code())
            } else {
                // Se lee el cuerpo de error para obtener `code`; nunca se registra.
                errorMapper.map(response.code(), response.errorBody()?.use { it.string() })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ApiResult.NetworkError
        } catch (_: SerializationException) {
            ApiResult.Unexpected(null)
        } catch (_: IllegalArgumentException) {
            ApiResult.Unexpected(null)
        }

        if (authenticated && result is ApiResult.Unauthenticated) {
            onSessionExpired()
        }
        return result
    }
}
