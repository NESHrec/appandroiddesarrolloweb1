package com.clinicaserena.app.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Solo debug. Nivel BASIC: método, URL, código y tiempo; nunca cuerpos de petición ni de respuesta,
 * para que ni contraseñas ni datos clínicos lleguen a logcat. `Authorization` se redacta por si el
 * nivel se sube en el futuro.
 */
object NetworkLogging {
    fun interceptors(): List<Interceptor> = listOf(
        createLoggingInterceptor { message -> Log.d("ClinicaSerenaHttp", message) },
    )
}

fun createLoggingInterceptor(logger: HttpLoggingInterceptor.Logger): HttpLoggingInterceptor =
    HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader(AuthInterceptor.HEADER_AUTHORIZATION)
    }
