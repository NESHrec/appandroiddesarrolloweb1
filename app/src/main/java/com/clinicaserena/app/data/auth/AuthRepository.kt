package com.clinicaserena.app.data.auth

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Identity
import com.clinicaserena.app.domain.model.PractitionerLinkStatus
import com.clinicaserena.app.domain.model.Role

/** Resultado de `login-unified` ya traducido a tipos de dominio. */
data class UnifiedLogin(
    val token: String,
    val accountType: AccountType,
    val role: Role,
    val expiresInSeconds: Long,
) {
    override fun toString(): String =
        "UnifiedLogin(token=<oculto>, accountType=$accountType, role=$role, expiresInSeconds=$expiresInSeconds)"
}

interface AuthRepository {
    suspend fun loginUnified(email: String, password: String): ApiResult<UnifiedLogin>

    /** Identidad vigente según `/auth/me` o `/staff/auth/me`; valida el token y el rol. */
    suspend fun currentIdentity(accountType: AccountType): ApiResult<Identity>

    /** Revoca la sesión en Spring (`/auth/logout` o `/staff/auth/logout`). */
    suspend fun logout(accountType: AccountType): ApiResult<Unit>

    /** Registro de paciente. Spring responde 202 con un mensaje genérico. */
    suspend fun register(fullName: String, email: String, password: String): ApiResult<String>

    suspend fun resendVerification(email: String): ApiResult<String>

    suspend fun requestPasswordRecovery(email: String): ApiResult<String>
}

class RemoteAuthRepository(
    private val api: AuthApi,
    private val apiCaller: ApiCaller,
) : AuthRepository {

    override suspend fun loginUnified(email: String, password: String): ApiResult<UnifiedLogin> =
        when (val result = apiCaller.call(authenticated = false) { api.loginUnified(LoginRequest(email, password)) }) {
            is ApiResult.Success -> result.data.toDomain()
            is ApiResult.Failure -> result
        }

    override suspend fun currentIdentity(accountType: AccountType): ApiResult<Identity> = when (accountType) {
        AccountType.PACIENTE ->
            when (val result = apiCaller.call(authenticated = true) { api.patientMe() }) {
                is ApiResult.Success -> ApiResult.Success(
                    Identity(subjectId = result.data.patientId, email = result.data.email, role = Role.PACIENTE),
                )
                is ApiResult.Failure -> result
            }
        AccountType.PERSONAL ->
            when (val result = apiCaller.call(authenticated = true) { api.staffMe() }) {
                is ApiResult.Success -> {
                    val data = result.data
                    val role = parseRole(data.role)
                    if (role == null) {
                        ApiResult.Unexpected(null)
                    } else {
                        ApiResult.Success(
                            Identity(
                                subjectId = data.accountId,
                                email = data.email,
                                role = role,
                                fullName = data.fullName,
                                practitionerLinkStatus = PractitionerLinkStatus.entries
                                    .firstOrNull { it.name == data.practitionerLinkStatus },
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> result
            }
    }

    // El Bearer se envía igual (la ruta no es @PublicEndpoint); `authenticated = false` solo evita que un
    // 401 aquí abra el re-login: SessionManager.logout ya trata ese 401 como "sesión cerrada".
    override suspend fun logout(accountType: AccountType): ApiResult<Unit> = when (accountType) {
        AccountType.PACIENTE -> apiCaller.callNoContent(authenticated = false) { api.patientLogout() }
        AccountType.PERSONAL -> apiCaller.callNoContent(authenticated = false) { api.staffLogout() }
    }

    override suspend fun register(fullName: String, email: String, password: String): ApiResult<String> =
        apiCaller.call(authenticated = false) { api.register(RegisterRequest(fullName, email, password)) }
            .mapMessage()

    override suspend fun resendVerification(email: String): ApiResult<String> =
        apiCaller.call(authenticated = false) { api.resendVerification(EmailRequest(email)) }.mapMessage()

    override suspend fun requestPasswordRecovery(email: String): ApiResult<String> =
        apiCaller.call(authenticated = false) { api.passwordRecovery(EmailRequest(email)) }.mapMessage()

    private fun ApiResult<GenericMessageResponse>.mapMessage(): ApiResult<String> = when (this) {
        is ApiResult.Success -> ApiResult.Success(data.message)
        is ApiResult.Failure -> this
    }

    private fun UnifiedLoginResponse.toDomain(): ApiResult<UnifiedLogin> {
        val type = AccountType.entries.firstOrNull { it.name == accountType }
        val parsedRole = parseRole(role)
        if (type == null || parsedRole == null || !tokenType.equals("Bearer", ignoreCase = true)) {
            return ApiResult.Unexpected(null)
        }
        return ApiResult.Success(UnifiedLogin(accessToken, type, parsedRole, expiresInSeconds))
    }

    private fun parseRole(value: String): Role? = Role.entries.firstOrNull { it.name == value }
}
