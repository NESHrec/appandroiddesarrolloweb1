package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

/**
 * Fondo de la app: color base con tres manchas difusas en la parte superior (turquesa arriba a la
 * izquierda, azul arriba a la derecha y violeta a la derecha), con alfa bajo. Es decorativo: no lleva
 * semántica y el texto siempre se apoya en tarjetas o en el color base, con su contraste comprobado.
 */
@Composable
fun SerenaBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalSerenaColors.current
    Box(
        modifier = modifier
            .background(colors.background)
            .drawBehind {
                val w = size.width
                val spots = listOf(
                    Offset(w * 0.05f, w * 0.05f) to w * 0.75f,
                    Offset(w * 0.95f, w * 0.10f) to w * 0.80f,
                    Offset(w * 1.00f, w * 0.75f) to w * 0.65f,
                )
                spots.forEachIndexed { index, (center, radius) ->
                    val color = colors.blobs.getOrNull(index) ?: return@forEachIndexed
                    drawCircle(
                        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
                }
            },
        content = content,
    )
}
