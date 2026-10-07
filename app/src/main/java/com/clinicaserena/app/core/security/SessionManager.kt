package com.clinicaserena.app.core.security

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.data.auth.AuthRepository
import com.clinicaserena.app.data.auth.UnifiedLogin
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

enum class LogoutReason {
    /** No había sesión o se descartó una sesión ilegible. */
    NONE,

    /** Spring respondió 401 `UNAUTHENTICATED` o el vencimiento local ya pasó. */
    EXPIRED,

    /** La cuenta es ADMIN o RECEPCION: fuera del alcance de la app. */
    UNSUPPORTED_ROLE,
}

sealed interface SessionState {
    data object Checking : SessionState
    data class LoggedOut(val reason: LogoutReason) : SessionState
    data class Active(val session: Session) : SessionState

    /** No se pudo validar la sesión guardada por red o servidor: recuperable con reintento. */
    data class RestoreFailed(val failure: ApiResult.Failure) : SessionState
}

/**
 * Fuente única de la sesión. El token vive en memoria para [AuthInterceptor] y cifrado en
 * [SessionStore]. Los borradores de formularios viven en sus ViewModel y no se tocan al vencer.
 */
class SessionManager(
    private val store: SessionStore,
    private val authRepository: () -> AuthRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Checking)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var token: String? = null
    private val restoreStarted = AtomicBoolean(false)

    fun currentToken(): String? = token

    /** Se llama una vez al arrancar la app. */
    fun restoreIfNeeded() {
        if (restoreStarted.compareAndSet(false, true)) {
            scope.launch { restore() }
        }
    }

    fun retryRestore() {
        scope.launch { restore() }
    }

    /**
     * Arranque con token guardado: primero se revisa `expiresAt` sin red; si sigue vigente se valida
     * con `/auth/me` o `/staff/auth/me` según `accountType`.
     */
    suspend fun restore() {
        _state.value = SessionState.Checking
        val stored = store.load()
        if (stored == null) {
            token = null
            _state.value = SessionState.LoggedOut(LogoutReason.NONE)
            return
        }
        if (!stored.expiresAt.isAfter(clock.instant())) {
            clearLocal(LogoutReason.EXPIRED)
            return
        }

        token = stored.token
        when (val result = authRepository().currentRole(stored.accountType)) {
            is ApiResult.Success ->
                if (result.data.supportedByApp) {
                    _state.value = SessionState.Active(stored.copy(role = result.data))
                } else {
                    revokeUnsupported(stored.accountType)
                }
            is ApiResult.Unauthenticated -> clearLocal(LogoutReason.EXPIRED)
            is ApiResult.NetworkError, is ApiResult.ServerError -> {
                token = null
                _state.value = SessionState.RestoreFailed(result)
            }
            is ApiResult.Failure -> clearLocal(LogoutReason.NONE)
        }
    }

    /**
     * Fase B: se llama tras un `login-unified` exitoso. ADMIN y RECEPCION se rechazan con mensaje y
     * se cierra su sesión en Spring de inmediato.
     */
    suspend fun startSession(login: UnifiedLogin) {
        if (!login.role.supportedByApp) {
            token = login.token
            revokeUnsupported(login.accountType)
            return
        }
        val lifetime = (login.expiresInSeconds - EXPIRY_MARGIN_SECONDS).coerceAtLeast(0)
        val session = Session(
            token = login.token,
            accountType = login.accountType,
            role = login.role,
            expiresAt = clock.instant().plusSeconds(lifetime),
        )
        token = session.token
        try {
            store.save(session)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin almacenamiento seguro la sesión sigue solo en memoria hasta cerrar la app.
        }
        _state.value = SessionState.Active(session)
    }

    /** Llamado por [com.clinicaserena.app.core.network.ApiCaller] ante un 401 `UNAUTHENTICATED`. */
    fun expire() {
        if (token == null && _state.value !is SessionState.Active) return
        token = null
        _state.value = SessionState.LoggedOut(LogoutReason.EXPIRED)
        scope.launch { clearStoreQuietly() }
    }

    private suspend fun revokeUnsupported(accountType: AccountType) {
        // Si la revocación falla por red, el token igual se descarta localmente y vence en Spring por TTL.
        authRepository().logout(accountType)
        clearLocal(LogoutReason.UNSUPPORTED_ROLE)
    }

    private suspend fun clearLocal(reason: LogoutReason) {
        token = null
        clearStoreQuietly()
        _state.value = SessionState.LoggedOut(reason)
    }

    private suspend fun clearStoreQuietly() {
        try {
            store.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // El token ya no está en memoria; si quedara en disco, el próximo arranque lo valida con Spring.
        }
    }

    private companion object {
        /** Margen para no usar un token que vence durante una petición. */
        const val EXPIRY_MARGIN_SECONDS = 30L
    }
}
