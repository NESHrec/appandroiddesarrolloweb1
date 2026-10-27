package com.clinicaserena.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.core.ui.theme.DayStatusColors
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.core.ui.theme.StatusColors
import com.clinicaserena.app.core.ui.theme.Tone

/**
 * Tono de una pastilla o aviso. INFO azul, WARNING ámbar (pendiente), SUCCESS verde (confirmada),
 * ERROR rosa (cancelada o error), TEAL turquesa (completada) y VIOLET violeta (llegada).
 */
enum class StatusTone { INFO, SUCCESS, WARNING, ERROR, TEAL, VIOLET }

fun StatusColors.tone(tone: StatusTone): Tone = when (tone) {
    StatusTone.INFO -> infoTone
    StatusTone.SUCCESS -> successTone
    StatusTone.WARNING -> warningTone
    StatusTone.ERROR -> errorTone
    StatusTone.TEAL -> tealTone
    StatusTone.VIOLET -> violetTone
}

/**
 * Pastilla de estado: fondo del tono, texto legible (≥ 4.5:1 en día y noche) y un punto de color, o un
 * ícono si se da [iconRes]. Siempre lleva texto: el color es solo apoyo. Con [onGradient] (dentro de una
 * tarjeta con degradado) conserva los colores de día también en modo noche.
 */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    onGradient: Boolean = false,
) {
    val colors = (if (onGradient) DayStatusColors else LocalStatusColors.current).tone(tone)
    Row(
        modifier = modifier
            .background(colors.background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.content)
        } else {
            Box(Modifier.size(8.dp).background(colors.dot, CircleShape))
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.content)
    }
}
