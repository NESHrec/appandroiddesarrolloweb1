package com.clinicaserena.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ErrorMapper
import com.clinicaserena.app.core.network.NetworkLogging
import com.clinicaserena.app.core.network.NetworkModule
import com.clinicaserena.app.core.security.KeystoreTokenCipher
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.security.SessionStore
import com.clinicaserena.app.data.auth.AuthApi
import com.clinicaserena.app.data.auth.AuthRepository
import com.clinicaserena.app.data.auth.RemoteAuthRepository
import com.clinicaserena.app.data.health.HealthApi
import com.clinicaserena.app.data.health.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/** Inyección de dependencias manual: un contenedor por proceso, creado en [ClinicaSerenaApp]. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val apiBaseUrl: String = BuildConfig.API_BASE_URL

    private val sessionStore by lazy {
        SessionStore(
            dataStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { appContext.preferencesDataStoreFile(SESSION_STORE_NAME) },
            ),
            cipher = KeystoreTokenCipher(),
        )
    }

    val sessionManager: SessionManager by lazy {
        SessionManager(
            store = sessionStore,
            authRepository = { authRepository },
            clock = Clock.systemUTC(),
            scope = appScope,
        )
    }

    private val apiCaller by lazy {
        ApiCaller(ErrorMapper(NetworkModule.json), onSessionExpired = { sessionManager.expire() })
    }

    private val retrofit by lazy {
        NetworkModule.retrofit(
            baseUrl = apiBaseUrl,
            client = NetworkModule.okHttpClient(
                tokenProvider = { sessionManager.currentToken() },
                extraInterceptors = NetworkLogging.interceptors(),
            ),
        )
    }

    val authRepository: AuthRepository by lazy {
        RemoteAuthRepository(retrofit.create(AuthApi::class.java), apiCaller)
    }

    val healthRepository: HealthRepository by lazy {
        HealthRepository(retrofit.create(HealthApi::class.java), apiCaller)
    }

    private companion object {
        const val SESSION_STORE_NAME = "session"
    }
}
