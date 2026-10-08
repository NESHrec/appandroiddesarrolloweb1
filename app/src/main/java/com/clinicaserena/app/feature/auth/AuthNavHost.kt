package com.clinicaserena.app.feature.auth

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clinicaserena.app.core.security.LogoutReason
import com.clinicaserena.app.core.ui.LocalAppContainer
import kotlinx.serialization.Serializable

@Serializable data object LoginRoute
@Serializable data object RegisterRoute
@Serializable data object PasswordRecoveryRoute
@Serializable data object ResendVerificationRoute

/** Acceso sin sesión: login, registro, recuperación y reenvío de verificación. */
@Composable
fun AuthNavHost(reason: LogoutReason, diagnostics: (@Composable () -> Unit)?) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(
                viewModel = viewModel { LoginViewModel(container.sessionManager) },
                reason = reason,
                onRegister = { navController.navigate(RegisterRoute) },
                onForgotPassword = { navController.navigate(PasswordRecoveryRoute) },
                onResendVerification = { navController.navigate(ResendVerificationRoute) },
                diagnostics = diagnostics,
            )
        }
        composable<RegisterRoute> {
            RegisterScreen(
                viewModel = viewModel { RegisterViewModel(container.authRepository) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PasswordRecoveryRoute> {
            EmailRequestScreen(
                mode = EmailRequestMode.PASSWORD_RECOVERY,
                viewModel = viewModel {
                    EmailRequestViewModel(EmailRequestMode.PASSWORD_RECOVERY, container.authRepository)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ResendVerificationRoute> {
            EmailRequestScreen(
                mode = EmailRequestMode.RESEND_VERIFICATION,
                viewModel = viewModel {
                    EmailRequestViewModel(EmailRequestMode.RESEND_VERIFICATION, container.authRepository)
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
