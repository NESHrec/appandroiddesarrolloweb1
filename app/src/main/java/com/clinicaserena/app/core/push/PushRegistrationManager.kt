package com.clinicaserena.app.core.push

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionEvents
import com.clinicaserena.app.core.security.SessionState
import com.clinicaserena.app.data.push.PushDeviceRepository
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

/** Estado de los recordatorios push de este teléfono para el paciente con sesión. */
sealed interface PushStatus {
    /** No hay sesión de PACIENTE (acceso, MEDICO o re-login pendiente). */
    data object Inactive : PushStatus

    /** Faltan el permiso de notificaciones (Android 13+) o están apagadas en los ajustes. */
    data object PermissionRequired : PushStatus
    data object Registering : PushStatus

    /** Spring respondió 204 al registro de esta sesión. */
    data object Registered : PushStatus

    /** 409 `PUSH_NOT_ENABLED` o 400 `PUSH_TOKEN_NOT_SIMULATED`: el servidor no tiene el envío habilitado. */
    data object ServerDisabled : PushStatus

    /** 401 al registrar: se reintenta solo cuando la persona vuelve a iniciar sesión. */
    data object AwaitingReauth : PushStatus

    /** FCM o Google Play Services no entregaron un token (sin red, sin Play Services). */
    data object TokenUnavailable : PushStatus
    data class Failed(val failure: ApiResult.Failure) : PushStatus
}

/** Token de Firebase Cloud Messaging de este teléfono. El valor nunca se registra ni se muestra. */
interface PushTokenSource {
    /** Devuelve el token vigente (lo crea si hace falta). Lanza si FCM no responde. */
    suspend fun token(): String

    /** Invalida el token en FCM: un envío posterior a ese token falla con `UNREGISTERED`. */
    suspend fun delete()
    fun setAutoInit(enabled: Boolean)
}

/** Si la app puede mostrar notificaciones (permiso de Android 13+ y ajustes del sistema). */
fun interface NotificationPermission {
    fun granted(): Boolean
}

/**
 * Marca persistente: "este teléfono tiene un token emitido para un paciente". Permite borrarlo aunque el
 * cierre de sesión haya ocurrido sin red. No guarda el token.
 */
interface PushTokenFlag {
    var issued: Boolean
}

/**
 * Registra el token FCM en Spring (`PUT /pacientes/me/dispositivos-push`) para cada sesión de PACIENTE
 * y lo retira (`POST …/retiro`) al cerrar sesión. Nunca pide token para MEDICO.
 *
 * El vencimiento de la sesión (30 min) no retira el dispositivo, igual que en Spring: el paciente sigue
 * recibiendo recordatorios aunque su sesión venza. Solo un cierre de sesión explícito, o que otra cuenta
 * entre en el teléfono, borran el token.
 */
class PushRegistrationManager(
    private val sessionState: StateFlow<SessionState>,
    private val repository: PushDeviceRepository,
    private val tokens: PushTokenSource,
    private val permission: NotificationPermission,
    private val tokenFlag: PushTokenFlag,
    private val scope: CoroutineScope,
) : SessionEvents {

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Inactive)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    private val mutex = Mutex()

    /**
     * Último registro que confirmó Spring: la sesión (por identidad, cada login crea una nueva) y el SHA-256
     * del token, solo en memoria. Evita repetir el PUT cuando FCM avisa un token que ya estaba registrado.
     */
    private class Registration(val session: Session, val tokenHash: String)

    @Volatile
    private var registered: Registration? = null

    /** Si Spring podría conservar el token de este teléfono; evita repetir el retiro en cada regreso a la app. */
    @Volatile
    private var serverMayHaveToken = true

    fun start() {
        scope.launch {
            sessionState.collectLatest { state ->
                when (state) {
                    is SessionState.Active -> when (state.session.role) {
                        Role.PACIENTE -> sync(state.session, force = false)
                        else -> {
                            // MEDICO en un teléfono que usó un paciente: su token no debe seguir activo.
                            registered = null
                            _status.value = PushStatus.Inactive
                            mutex.withLock { deleteTokenIfIssued() }
                        }
                    }
                    is SessionState.ReauthRequired -> if (_status.value == PushStatus.Registering) {
                        _status.value = PushStatus.AwaitingReauth
                    }
                    else -> {
                        registered = null
                        _status.value = PushStatus.Inactive
                    }
                }
            }
        }
    }

    /** Al volver a la app o tras responder el diálogo de permiso: registra si hace falta. */
    fun refresh() {
        val session = activePatient() ?: return
        scope.launch { sync(session, force = false) }
    }

    /** Botón "Reintentar". */
    fun retry() {
        val session = activePatient() ?: return
        scope.launch { sync(session, force = true) }
    }

    /** FCM renovó el token: se registra si cambió. */
    fun onNewToken() {
        val session = activePatient() ?: return
        scope.launch { sync(session, force = false) }
    }

    /**
     * Si un mensaje recibido en primer plano debe mostrarse: el token es de un paciente de este teléfono.
     * Como en segundo plano, se muestra aunque su sesión haya vencido; tras un cierre de sesión ya no.
     */
    fun remindersExpected(): Boolean = tokenFlag.issued

    /**
     * Antes de `/auth/logout`, con el Bearer todavía válido. Es de mejor esfuerzo: Spring también retira
     * al cerrar la sesión que registró el dispositivo, y después se borra el token en FCM.
     */
    override suspend fun beforeLogout(session: Session) {
        if (session.role != Role.PACIENTE) return
        mutex.withLock {
            registered = null
            if (!tokenFlag.issued) return
            withTimeoutOrNull(LOGOUT_REMOVE_TIMEOUT_MS) {
                val token = tokenOrNull() ?: return@withTimeoutOrNull
                repository.remove(token)
            }
        }
    }

    /**
     * La sesión se cerró en este teléfono por decisión del usuario. El borrado en FCM corre en el scope de
     * la app: quien llama (el ViewModel del cierre de sesión) se descarta en cuanto la UI vuelve al acceso.
     */
    override suspend fun signedOut() {
        registered = null
        _status.value = PushStatus.Inactive
        scope.launch { mutex.withLock { deleteTokenIfIssued() } }
    }

    private fun activePatient(): Session? =
        (sessionState.value as? SessionState.Active)?.session?.takeIf { it.role == Role.PACIENTE }

    private suspend fun sync(session: Session, force: Boolean) = mutex.withLock {
        if (!permission.granted()) {
            // Sin permiso las notificaciones no se verían: se retira el token si quedó registrado.
            if (tokenFlag.issued && serverMayHaveToken) {
                val removed = tokenOrNull()?.let { repository.remove(it) }
                if (removed is ApiResult.Success) serverMayHaveToken = false
            }
            registered = null
            _status.value = PushStatus.PermissionRequired
            return@withLock
        }
        if (registered?.session !== session) _status.value = PushStatus.Registering
        tokenFlag.issued = true
        tokens.setAutoInit(true)
        val token = tokenOrNull()
        if (token == null) {
            _status.value = PushStatus.TokenUnavailable
            return@withLock
        }
        val tokenHash = sha256(token)
        val previous = registered
        if (!force && previous != null && previous.session === session && previous.tokenHash == tokenHash) {
            _status.value = PushStatus.Registered
            return@withLock
        }
        _status.value = PushStatus.Registering
        serverMayHaveToken = true
        _status.value = when (val result = repository.register(token)) {
            is ApiResult.Success -> {
                registered = Registration(session, tokenHash)
                PushStatus.Registered
            }
            // ApiCaller ya abrió el re-login; la sesión nueva vuelve a registrar.
            is ApiResult.Unauthenticated -> PushStatus.AwaitingReauth
            is ApiResult.Conflict -> if (result.code == CODE_NOT_ENABLED) PushStatus.ServerDisabled else PushStatus.Failed(result)
            is ApiResult.BadRequest -> if (result.code == CODE_NOT_SIMULATED) PushStatus.ServerDisabled else PushStatus.Failed(result)
            is ApiResult.Failure -> PushStatus.Failed(result)
        }
    }

    private suspend fun deleteTokenIfIssued() {
        if (!tokenFlag.issued) return
        tokens.setAutoInit(false)
        try {
            tokens.delete()
            tokenFlag.issued = false
            serverMayHaveToken = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red: la marca sigue y se reintenta en el próximo cierre de sesión o login de otra cuenta.
        }
    }

    private suspend fun tokenOrNull(): String? = try {
        tokens.token()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // La excepción de Firebase no se registra: podría incluir datos del dispositivo.
        null
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CODE_NOT_ENABLED = "PUSH_NOT_ENABLED"
        const val CODE_NOT_SIMULATED = "PUSH_TOKEN_NOT_SIMULATED"
        const val LOGOUT_REMOVE_TIMEOUT_MS = 5_000L
    }
}
