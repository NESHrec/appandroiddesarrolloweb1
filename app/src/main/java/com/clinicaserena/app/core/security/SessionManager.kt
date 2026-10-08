package com.clinicaserena.app.core.security

import com.clinicaserena.app.BuildConfig
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.data.auth.AuthRepository
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

enum class LogoutReason {
    /** No había sesión, el usuario cerró sesión o se descartó una sesión ilegible. */
    NONE,

    /** Spring respondió 401 `UNAUTHENTICATED` al arrancar o el vencimiento local ya pasó. */
    EXPIRED,

    /** La cuenta es ADMIN o RECEPCION: fuera del alcance de la app. */
    UNSUPPORTED_ROLE,

    /** El logout no llegó al servidor y el usuario cerró solo en este dispositivo. */
    LOCAL_ONLY,
}

sealed interface SessionState {
    data object Checking : SessionState
    data class LoggedOut(val reason: LogoutReason) : SessionState
    data class Active(val session: Session) : SessionState

    /**
     * Spring rechazó el token (401) con la sesión en uso. El grafo del rol sigue en pantalla, con sus
     * borradores en memoria, y encima se pide la contraseña de nuevo.
     */
    data class ReauthRequired(val previous: Session) : SessionState

    /** No se pudo validar la sesión guardada por red o servidor: recuperable con reintento. */
    data class RestoreFailed(val failure: ApiResult.Failure) : SessionState
}

sealed interface LoginOutcome {
    data object Success : LoginOutcome

    /** ADMIN o RECEPCION: su sesión ya se cerró en Spring. */
    data object UnsupportedRole : LoginOutcome
    data class Failed(val failure: ApiResult.Failure) : LoginOutcome
}

sealed interface LogoutOutcome {
    data object Done : LogoutOutcome

    /** Spring no confirmó el cierre (red o servidor). La sesión local sigue intacta. */
    data class Failed(val failure: ApiResult.Failure) : LogoutOutcome
}

/**
 * Fuente única de la sesión. El token vive en memoria para el `AuthInterceptor` y cifrado en
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

    private val _reauthenticated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emite cuando la misma persona vuelve a entrar tras [SessionState.ReauthRequired]: reintentar lo pendiente. */
    val reauthenticated: SharedFlow<Unit> = _reauthenticated.asSharedFlow()

    @Volatile
    private var token: String? = null
    private val restoreStarted = AtomicBoolean(false)
    private val loginMutex = Mutex()

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
        when (val result = authRepository().currentIdentity(stored.accountType)) {
            is ApiResult.Success -> {
                val identity = result.data
                if (identity.role.supportedByApp && identity.subjectId == stored.subjectId) {
                    _state.value = SessionState.Active(stored.copy(role = identity.role, email = identity.email))
                } else if (!identity.role.supportedByApp) {
                    revokeUnsupported(stored.accountType)
                } else {
                    clearLocal(LogoutReason.NONE)
                }
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
     * Login unificado sin selector de rol: `login-unified` → `me` (identidad y rol vigentes).
     * ADMIN y RECEPCION se rechazan y su sesión se cierra en Spring de inmediato. Si había una
     * sesión vencida de la misma persona, se conserva el grafo y se emite [reauthenticated].
     */
    suspend fun login(email: String, password: String): LoginOutcome = loginMutex.withLock {
        val previous = (_state.value as? SessionState.ReauthRequired)?.previous

        val login = when (val result = authRepository().loginUnified(email.trim(), password)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return LoginOutcome.Failed(result)
        }
        token = login.token
        if (!login.role.supportedByApp) {
            revokeUnsupported(login.accountType)
            return LoginOutcome.UnsupportedRole
        }

        val identity = when (val result = authRepository().currentIdentity(login.accountType)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                token = null
                return LoginOutcome.Failed(result)
            }
        }
        if (!identity.role.supportedByApp) {
            revokeUnsupported(login.accountType)
            return LoginOutcome.UnsupportedRole
        }

        val lifetime = (login.expiresInSeconds - EXPIRY_MARGIN_SECONDS).coerceAtLeast(0)
        val session = Session(
            token = login.token,
            accountType = login.accountType,
            role = identity.role,
            expiresAt = clock.instant().plusSeconds(lifetime),
            subjectId = identity.subjectId,
            email = identity.email,
        )
        try {
            store.save(session)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin almacenamiento seguro la sesión sigue solo en memoria hasta cerrar la app.
        }
        _state.value = SessionState.Active(session)
        if (previous != null && previous.subjectId == session.subjectId) {
            _reauthenticated.tryEmit(Unit)
        }
        LoginOutcome.Success
    }

    /**
     * Cierra la sesión en Spring. Solo un 204, o un 401 (ya no existía), limpian la sesión local;
     * si el servidor no responde no se simula un cierre exitoso.
     */
    suspend fun logout(): LogoutOutcome {
        val session = (_state.value as? SessionState.Active)?.session
        if (session == null) {
            clearLocal(LogoutReason.NONE)
            return LogoutOutcome.Done
        }
        return when (val result = authRepository().logout(session.accountType)) {
            is ApiResult.Success, is ApiResult.Unauthenticated -> {
                clearLocal(LogoutReason.NONE)
                LogoutOutcome.Done
            }
            is ApiResult.Failure -> LogoutOutcome.Failed(result)
        }
    }

    /** El usuario eligió cerrar solo en este dispositivo: la sesión del servidor vence sola por TTL. */
    suspend fun logoutLocalOnly() {
        clearLocal(LogoutReason.LOCAL_ONLY)
    }

    /** Desde la hoja de re-login: abandona la sesión vencida (su token ya no sirve) y descarta borradores. */
    suspend fun abandonExpiredSession() {
        clearLocal(LogoutReason.NONE)
    }

    /** Llamado por [com.clinicaserena.app.core.network.ApiCaller] ante un 401 `UNAUTHENTICATED`. */
    fun expire() {
        val current = _state.value as? SessionState.Active ?: return
        token = null
        _state.value = SessionState.ReauthRequired(current.session)
        scope.launch { clearStoreQuietly() }
    }

    /**
     * Solo debug: reemplaza el token en memoria por uno inválido para que Spring responda un 401 real
     * en la siguiente petición. No toca el token real guardado ni lo expone.
     */
    fun debugInvalidateToken() {
        if (!BuildConfig.DEBUG) return
        if (_state.value is SessionState.Active) token = DEBUG_INVALID_TOKEN
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
        const val DEBUG_INVALID_TOKEN = "token-invalido-simulado"
    }
}
