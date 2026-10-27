package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors
import com.clinicaserena.app.core.ui.theme.SectionHeaderStyle

/** Encabezado de sección de una lista agrupada: 13 sp, semibold, en mayúsculas, color secundario. */
@Composable
fun GroupedSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SectionHeaderStyle,
        color = LocalSerenaColors.current.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

/** Tarjeta de una lista agrupada: blanca (día) o #161D2E (noche), esquinas de 18 dp y sombra suave. */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSerenaColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = colors.cardShadow, spotColor = colors.cardShadow),
        shape = shape,
        color = colors.card,
        contentColor = colors.text,
    ) {
        Column(content = content)
    }
}

/**
 * Fila de una lista agrupada. Si tiene [onClick], toda la fila es un botón (48 dp mínimo) y muestra
 * un chevron a la derecha. [leading] y [trailing] son opcionales (avatar, ícono, estado).
 */
@Composable
fun GroupedRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    testTag: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        if (trailing != null) trailing()
        if (showChevron) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = LocalSerenaColors.current.chevron,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Separador fino (0.5 dp). Dentro de una tarjeta agrupada se usa con [startIndent] = sangría del texto. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        thickness = 0.5.dp,
        color = LocalSerenaColors.current.separator,
    )
}
