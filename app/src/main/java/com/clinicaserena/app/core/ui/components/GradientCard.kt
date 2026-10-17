package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

private val GradientShape = RoundedCornerShape(24.dp)

/**
 * Tarjeta destacada con el degradado principal ([alt]: el alterno turquesa → azul), resplandor y dos
 * anillos decorativos arriba a la derecha (blanco al 10 %, lejos del texto). Todo el texto va en blanco
 * pleno (≥ 4.7:1 sobre cualquier punto del degradado). Si tiene [onClick], la tarjeta entera es un botón.
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    alt: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSerenaColors.current
    val gradient = if (alt) colors.gradientAlt else colors.gradient
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, GradientShape, ambientColor = gradient.last(), spotColor = gradient.last())
            .clip(GradientShape)
            .background(Brush.linearGradient(gradient))
            .drawBehind {
                val ring = Color.White.copy(alpha = 0.10f)
                val w = size.width
                drawCircle(ring, radius = w * 0.26f, center = Offset(w * 0.92f, w * 0.02f), style = Stroke(width = 16.dp.toPx()))
                drawCircle(ring, radius = w * 0.12f, center = Offset(w * 1.0f, w * 0.30f), style = Stroke(width = 10.dp.toPx()))
            }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onGradient) { content() }
    }
}

/**
 * Franja dentro de una [GradientCard] (por ejemplo, el médico de la próxima cita): relleno blanco muy
 * tenue (6 %) y borde blanco al 30 %, para que el texto blanco siga en ≥ 4.6:1.
 */
@Composable
fun GradientStrip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Etiqueta pequeña de dato (dosis, frecuencia, duración…): fondo de la pista con texto normal. */
@Composable
fun InfoTag(text: String, modifier: Modifier = Modifier) {
    val colors = LocalSerenaColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.text,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.segmentTrack)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
