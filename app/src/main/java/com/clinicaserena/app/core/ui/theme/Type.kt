package com.clinicaserena.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/**
 * Escala inspirada en iOS con la fuente del sistema:
 * - headlineLarge: título grande de las pantallas principales (34 sp).
 * - headlineMedium: título de las pantallas de flujo (28 sp).
 * - titleMedium / bodyLarge: 17 sp; bodyMedium: secundario 15 sp; bodySmall: pie 13 sp.
 * Los encabezados de sección (13 sp, en mayúsculas) están en [SectionHeaderStyle].
 */
val SerenaTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, -0.6),
    displayMedium = style(45, 52, FontWeight.Bold, -0.6),
    displaySmall = style(36, 42, FontWeight.Bold, -0.6),
    headlineLarge = style(34, 41, FontWeight.Bold, -0.6),
    headlineMedium = style(28, 34, FontWeight.Bold, -0.4),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.3),
    titleLarge = style(20, 25, FontWeight.SemiBold, -0.2),
    titleMedium = style(17, 22, FontWeight.SemiBold, -0.2),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(17, 22, tracking = -0.2),
    bodyMedium = style(15, 20),
    bodySmall = style(13, 18),
    labelLarge = style(15, 20, FontWeight.Medium),
    labelMedium = style(13, 18, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium),
)

/** Encabezado de sección de las listas agrupadas: 13 sp, semibold, en mayúsculas y con tracking. */
val SectionHeaderStyle = style(13, 18, FontWeight.SemiBold, 0.6)

/** Texto de los botones principal y secundario. */
val ButtonTextStyle = style(17, 22, FontWeight.SemiBold, -0.2)
