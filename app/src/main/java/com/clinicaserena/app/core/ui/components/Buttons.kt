package com.clinicaserena.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.core.ui.theme.ButtonTextStyle
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

/** Forma de los botones grandes: 50 dp de alto y esquinas de 14 dp. */
val ButtonShape = RoundedCornerShape(14.dp)
private val ButtonMinHeight = 50.dp
private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

/**
 * Botón principal: degradado azul → violeta con texto blanco (5.2–5.7:1) y un resplandor suave.
 * Ocupa todo el ancho salvo que [fillWidth] sea `false`. Con [loading] se deshabilita y muestra progreso.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes iconRes: Int? = null,
    fillWidth: Boolean = true,
) {
    val colors = LocalSerenaColors.current
    val active = enabled && !loading
    Button(
        onClick = onClick,
        enabled = active,
        shape = ButtonShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onGradient,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = if (loading) colors.onGradient else colors.textSecondary,
        ),
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = ButtonMinHeight)
            .then(
                if (active) {
                    Modifier.shadow(10.dp, ButtonShape, ambientColor = colors.gradient.last(), spotColor = colors.gradient.last())
                } else {
                    Modifier
                },
            )
            .clip(ButtonShape)
            // Deshabilitado: gris de la pista con texto secundario (inconfundible en día y noche). Mientras
            // envía (loading) conserva el degradado con el indicador de progreso.
            .background(
                if (active || loading) Brush.horizontalGradient(colors.gradient) else SolidColor(colors.segmentTrack),
            ),
    ) {
        ButtonContent(text, loading, iconRes, colors.onGradient)
    }
}

/** Botón secundario: tarjeta con borde fino y texto azul oscuro (día) o azul claro (noche). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes iconRes: Int? = null,
    fillWidth: Boolean = true,
) {
    val colors = LocalSerenaColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = ButtonShape,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, colors.secondaryButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.secondaryButton,
            contentColor = colors.secondaryButtonText,
        ),
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = ButtonMinHeight),
    ) {
        ButtonContent(text, loading, iconRes, colors.secondaryButtonText)
    }
}

/** Enlace de texto (48 dp de alto mínimo): azul en día (4.8:1) y azul claro en noche (7.5:1). */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalSerenaColors.current
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text,
            style = ButtonTextStyle,
            color = if (muted) colors.textSecondary else colors.link,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    @DrawableRes iconRes: Int?,
    progressColor: Color,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = progressColor)
        Spacer(Modifier.width(8.dp))
    } else if (iconRes != null) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = ButtonTextStyle, textAlign = TextAlign.Center, modifier = modifier)
}
