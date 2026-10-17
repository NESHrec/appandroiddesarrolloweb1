package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

private val BarInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/** Barra de las pantallas principales: título grande (34 sp) a la izquierda y acciones a la derecha. */
@Composable
fun LargeTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(BarInsets)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

/**
 * Barra de las pantallas de detalle y de flujo: «‹ Atrás» en acento a la izquierda, título centrado
 * y acciones opcionales a la derecha. El título nunca se monta sobre los botones: se centra en el
 * espacio libre y, con letra grande, ocupa hasta dos líneas.
 */
@Composable
fun CenteredBackBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backLabel = stringResource(R.string.navegar_atras)
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(BarInsets)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        content = {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("nav_back")
                    // Igual que el botón anterior: un solo nodo «Atrás», con rol de botón y su acción.
                    .clearAndSetSemantics {
                        contentDescription = backLabel
                        role = Role.Button
                        onClick { onBack(); true }
                    }
                    .clickable(onClick = onBack)
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_left),
                    contentDescription = null,
                    tint = LocalSerenaColors.current.link,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    backLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalSerenaColors.current.link,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val back = measurables[0].measure(loose)
        val end = measurables[2].measure(loose)
        val side = maxOf(back.width, end.width)
        val titleWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)
        val titlePlaceable = measurables[1].measure(Constraints(maxWidth = titleWidth))
        val height = maxOf(back.height, end.height, titlePlaceable.height, constraints.minHeight)
        layout(constraints.maxWidth, height) {
            back.place(0, (height - back.height) / 2)
            titlePlaceable.place((constraints.maxWidth - titlePlaceable.width) / 2, (height - titlePlaceable.height) / 2)
            end.place(constraints.maxWidth - end.width, (height - end.height) / 2)
        }
    }
}
