package com.clinicaserena.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Fondos de estado de la paleta. Siempre van acompañados de icono y texto, nunca solos. */
@Immutable
data class StatusColors(
    val info: Color,
    val warning: Color,
    val success: Color,
    val error: Color,
    val onStatus: Color,
)

private val SerenaStatusColors = StatusColors(
    info = SerenaTurquesa,
    warning = SerenaAmarillo,
    success = SerenaVerde,
    error = SerenaRosa,
    onStatus = SerenaTexto,
)

val LocalStatusColors = staticCompositionLocalOf { SerenaStatusColors }

private val SerenaColorScheme = lightColorScheme(
    primary = SerenaPrincipal,
    onPrimary = SerenaSuperficie,
    primaryContainer = SerenaTurquesa,
    onPrimaryContainer = SerenaTexto,
    secondary = SerenaPrincipal,
    onSecondary = SerenaSuperficie,
    secondaryContainer = SerenaTurquesa,
    onSecondaryContainer = SerenaTexto,
    tertiary = SerenaPrincipal,
    onTertiary = SerenaSuperficie,
    tertiaryContainer = SerenaAmarillo,
    onTertiaryContainer = SerenaTexto,
    background = SerenaFondo,
    onBackground = SerenaTexto,
    surface = SerenaSuperficie,
    onSurface = SerenaTexto,
    surfaceVariant = SerenaFondo,
    onSurfaceVariant = SerenaTextoSecundario,
    surfaceContainer = SerenaSuperficie,
    surfaceContainerLow = SerenaSuperficie,
    surfaceContainerHigh = SerenaFondo,
    outline = SerenaBorde,
    outlineVariant = SerenaBorde,
    error = SerenaErrorFuerte,
    onError = SerenaSuperficie,
    errorContainer = SerenaRosa,
    onErrorContainer = SerenaTexto,
)

/**
 * Tema de la app. Solo modo claro: la paleta acordada no define variante oscura.
 * Tipografía del sistema (Material 3 por defecto), que respeta el tamaño de fuente del dispositivo.
 */
@Composable
fun ClinicaSerenaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides SerenaStatusColors) {
        MaterialTheme(
            colorScheme = SerenaColorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
