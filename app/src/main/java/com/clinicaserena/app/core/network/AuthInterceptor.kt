package com.clinicaserena.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/** Marca un método de Retrofit como público: no se le adjunta el Bearer (login, health, catálogo). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicEndpoint

/**
 * Adjunta `Authorization: Bearer` a las peticiones no públicas cuando hay sesión en memoria.
 * El token se lee en cada petición desde [tokenProvider]; nunca se copia a logs.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isPublic = request.tag(Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(PublicEndpoint::class.java) == true
        val token = tokenProvider()
        if (isPublic || token == null) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header(HEADER_AUTHORIZATION, "Bearer $token")
                .build(),
        )
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
