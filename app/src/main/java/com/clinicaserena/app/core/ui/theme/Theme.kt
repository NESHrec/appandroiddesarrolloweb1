package com.clinicaserena.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Un tono de estado: fondo, texto legible sobre ese fondo y el punto de color de las pastillas. */
@Immutable
data class Tone(val background: Color, val content: Color, val dot: Color)

/**
 * Tonos de estado (pastillas y avisos). Siempre van con texto o ícono: el color es solo apoyo.
 * [info], [warning], [success] y [error] son los fondos que reciben los avisos ([contentFor] da su texto).
 */
@Immutable
data class StatusColors(
    val infoTone: Tone,
    val warningTone: Tone,
    val successTone: Tone,
    val errorTone: Tone,
    val tealTone: Tone,
    val violetTone: Tone,
    val onStatus: Color,
) {
    val info: Color get() = infoTone.background
    val warning: Color get() = warningTone.background
    val success: Color get() = successTone.background
    val error: Color get() = errorTone.background

    private val tones get() = listOf(infoTone, warningTone, successTone, errorTone, tealTone, violetTone)

    /** Texto e íconos legibles sobre [background] (uno de los fondos de estado); si no lo es, el texto normal. */
    fun contentFor(background: Color): Color = tones.firstOrNull { it.background == background }?.content ?: onStatus
}

/** Tokens del rediseño que Material 3 no cubre (degradados, manchas, pestañas, chips, avatares…). */
@Immutable
data class SerenaColors(
    val isDark: Boolean,
    val background: Color,
    val blobs: List<Color>,
    val card: Color,
    val cardShadow: Color,
    val separator: Color,
    val text: Color,
    val textSecondary: Color,
    val chevron: Color,
    val link: Color,
    val gradient: List<Color>,
    val gradientAlt: List<Color>,
    val onGradient: Color,
    val secondaryButton: Color,
    val secondaryButtonBorder: Color,
    val secondaryButtonText: Color,
    val segmentTrack: Color,
    val segmentText: Color,
    val chipSelected: Color,
    val chipSelectedText: Color,
    val chip: Color,
    val chipText: Color,
    val chipBorder: Color,
    val tabBar: Color,
    val tabBarBorder: Color,
    val tabIconInactive: Color,
    val tabLabelInactive: Color,
    val fieldBorder: Color,
    val avatarBlue: Tone,
    val avatarViolet: Tone,
)

private val DayStatus = StatusColors(
    infoTone = Tone(Color(0xFFDBEAFE), Color(0xFF1E40AF), EstadoAzul),
    warningTone = Tone(Color(0xFFFEF3C7), Color(0xFF92400E), EstadoAmbar),
    successTone = Tone(Color(0xFFDCFCE7), Color(0xFF166534), EstadoVerde),
    errorTone = Tone(Color(0xFFFCE7F3), Color(0xFF9D174D), EstadoRosa),
    tealTone = Tone(Color(0xFFCCFBF1), Color(0xFF0F766E), EstadoTurquesa),
    violetTone = Tone(Color(0xFFEDE9FE), Color(0xFF5B21B6), EstadoVioleta),
    onStatus = DiaTexto,
)

private val NightStatus = StatusColors(
    infoTone = Tone(EstadoAzul.copy(alpha = 0.16f), Color(0xFF93C5FD), EstadoAzul),
    warningTone = Tone(EstadoAmbar.copy(alpha = 0.16f), Color(0xFFFCD34D), EstadoAmbar),
    successTone = Tone(EstadoVerde.copy(alpha = 0.16f), Color(0xFF86EFAC), EstadoVerde),
    errorTone = Tone(EstadoRosa.copy(alpha = 0.16f), Color(0xFFF9A8D4), EstadoRosa),
    tealTone = Tone(EstadoTurquesa.copy(alpha = 0.16f), Color(0xFF5EEAD4), EstadoTurquesa),
    violetTone = Tone(EstadoVioleta.copy(alpha = 0.20f), Color(0xFFC4B5FD), EstadoVioleta),
    onStatus = NocheTexto,
)

/** Tonos de día: dentro de una tarjeta con degradado las pastillas conservan sus colores de día. */
val DayStatusColors: StatusColors = DayStatus

private val DayColors = SerenaColors(
    isDark = false,
    background = DiaFondo,
    blobs = listOf(Color(0xFF5EEAD4).copy(alpha = 0.35f), Color(0xFF60A5FA).copy(alpha = 0.35f), Color(0xFFA78BFA).copy(alpha = 0.22f)),
    card = DiaTarjeta,
    cardShadow = Color(0xFF1E3A8A).copy(alpha = 0.10f),
    separator = DiaSeparador,
    text = DiaTexto,
    textSecondary = DiaTextoSecundario,
    chevron = DiaChevron,
    link = DiaEnlace,
    gradient = listOf(GradienteInicio, GradienteFin),
    gradientAlt = listOf(GradienteAltInicio, GradienteAltFin),
    onGradient = Color.White,
    secondaryButton = DiaTarjeta,
    secondaryButtonBorder = DiaSeparador,
    secondaryButtonText = DiaSecundarioTexto,
    segmentTrack = DiaPista,
    segmentText = DiaSegmentoTexto,
    chipSelected = DiaTexto,
    chipSelectedText = Color.White,
    chip = DiaTarjeta,
    chipText = DiaTexto,
    chipBorder = DiaSeparador,
    tabBar = DiaBarraPestanas,
    tabBarBorder = Color.Transparent,
    tabIconInactive = DiaPestanaIcono,
    tabLabelInactive = DiaPestanaEtiqueta,
    fieldBorder = DiaBordeCampo,
    avatarBlue = Tone(Color(0xFFDBEAFE), Color(0xFF1E40AF), EstadoAzul),
    avatarViolet = Tone(Color(0xFFEDE9FE), Color(0xFF5B21B6), EstadoVioleta),
)

private val NightColors = SerenaColors(
    isDark = true,
    background = NocheFondo,
    blobs = listOf(ManchaTurquesa.copy(alpha = 0.30f), ManchaAzul.copy(alpha = 0.30f), ManchaVioleta.copy(alpha = 0.26f)),
    card = NocheTarjeta,
    cardShadow = Color.Black.copy(alpha = 0.45f),
    separator = NocheSeparador,
    text = NocheTexto,
    textSecondary = NocheTextoSecundario,
    chevron = NocheChevron,
    link = NocheEnlace,
    gradient = listOf(GradienteInicio, GradienteFin),
    gradientAlt = listOf(GradienteAltInicio, GradienteAltFin),
    onGradient = Color.White,
    secondaryButton = NocheTarjeta,
    secondaryButtonBorder = NocheSeparador,
    secondaryButtonText = NocheSecundarioTexto,
    segmentTrack = NochePista,
    segmentText = NocheSegmentoTexto,
    chipSelected = NocheTexto,
    chipSelectedText = NocheFondo,
    chip = NocheTarjeta,
    chipText = NocheSegmentoTexto,
    chipBorder = NocheSeparador,
    tabBar = NocheBarraPestanas,
    tabBarBorder = Color.White.copy(alpha = 0.08f),
    tabIconInactive = NochePestanaIcono,
    tabLabelInactive = NocheTextoSecundario,
    fieldBorder = NocheBordeCampo,
    avatarBlue = Tone(Color(0xFF1E3A8A), Color(0xFFBFDBFE), EstadoAzul),
    avatarViolet = Tone(Color(0xFF3B2A75), Color(0xFFDDD6FE), EstadoVioleta),
)

val LocalStatusColors = staticCompositionLocalOf { DayStatus }
val LocalSerenaColors = staticCompositionLocalOf { DayColors }

private val DayScheme = lightColorScheme(
    primary = DiaEnlace,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = DiaSecundarioTexto,
    secondary = DiaEnlace,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = DiaSecundarioTexto,
    tertiary = GradienteFin,
    onTertiary = Color.White,
    background = DiaFondo,
    onBackground = DiaTexto,
    surface = DiaTarjeta,
    onSurface = DiaTexto,
    surfaceVariant = DiaFondo,
    onSurfaceVariant = DiaTextoSecundario,
    surfaceContainerLowest = DiaTarjeta,
    surfaceContainerLow = DiaTarjeta,
    surfaceContainer = DiaTarjeta,
    surfaceContainerHigh = DiaTarjeta,
    surfaceContainerHighest = DiaTarjeta,
    outline = DiaBordeCampo,
    outlineVariant = DiaSeparador,
    error = DiaError,
    onError = Color.White,
    errorContainer = Color(0xFFFCE7F3),
    onErrorContainer = Color(0xFF9D174D),
)

private val NightScheme = darkColorScheme(
    primary = NocheEnlace,
    onPrimary = NocheFondo,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = NocheSecundarioTexto,
    secondary = NocheEnlace,
    onSecondary = NocheFondo,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = NocheSecundarioTexto,
    tertiary = Color(0xFFC4B5FD),
    onTertiary = NocheFondo,
    background = NocheFondo,
    onBackground = NocheTexto,
    surface = NocheTarjeta,
    onSurface = NocheTexto,
    surfaceVariant = NochePista,
    onSurfaceVariant = NocheTextoSecundario,
    surfaceContainerLowest = NocheFondo,
    surfaceContainerLow = NocheTarjeta,
    surfaceContainer = NocheTarjeta,
    surfaceContainerHigh = NocheTarjeta,
    surfaceContainerHighest = NochePista,
    outline = NocheBordeCampo,
    outlineVariant = NocheSeparador,
    error = Color(0xFFF9A8D4),
    onError = NocheFondo,
    errorContainer = EstadoRosa.copy(alpha = 0.16f),
    onErrorContainer = Color(0xFFF9A8D4),
)

/** Tarjetas y grupos con esquinas de 14–20 dp, como las listas agrupadas de iOS. */
private val SerenaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Tema de la app. Sigue el modo del sistema ([isSystemInDarkTheme]): no hay interruptor dentro de la
 * app ni se guarda ninguna preferencia. Tipografía del sistema con la escala del rediseño
 * ([SerenaTypography]), en `sp` para respetar el tamaño de fuente del dispositivo.
 */
@Composable
fun ClinicaSerenaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) NightStatus else DayStatus,
        LocalSerenaColors provides if (darkTheme) NightColors else DayColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) NightScheme else DayScheme,
            typography = SerenaTypography,
            shapes = SerenaShapes,
            content = content,
        )
    }
}
