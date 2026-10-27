package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

/**
 * Control segmentado (Próximas/Historial, Hoy/Esta semana/Este mes…). Para accesibilidad se comporta
 * como una fila de pestañas: cada segmento es `Role.Tab` con su estado `selected`. El segmento elegido
 * se distingue por el degradado con texto blanco y en negrita, no solo por el color.
 * Con letra grande los textos pueden ocupar dos líneas.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTags: List<String?> = emptyList(),
) {
    val colors = LocalSerenaColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.segmentTrack)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .then(if (selected) Modifier.shadow(6.dp, shape, ambientColor = colors.gradient.last(), spotColor = colors.gradient.last()) else Modifier)
                    .clip(shape)
                    .background(if (selected) Brush.horizontalGradient(colors.gradient) else SolidColor(Color.Transparent))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .then(testTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                SegmentLabel(label, selected, if (selected) colors.onGradient else colors.segmentText)
            }
        }
    }
}

/**
 * Etiqueta de un segmento: hasta dos líneas y nunca parte una palabra. Si el texto real termina una
 * línea a media palabra (por ejemplo «Seguimientos» con letra 1.5x en pantalla angosta), reduce el
 * tamaño un 8 % por paso, solo lo necesario y como máximo al 70 %.
 */
@Composable
private fun SegmentLabel(label: String, selected: Boolean, color: Color) {
    val base = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    var scale by remember(label, selected) { mutableFloatStateOf(1f) }
    Text(
        text = label,
        style = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        onTextLayout = { layout ->
            val brokenWord = (0 until layout.lineCount - 1).any { line ->
                val end = layout.getLineEnd(line)
                end in 1 until label.length && label[end - 1] != ' ' && label[end] != ' '
            }
            if ((brokenWord || layout.hasVisualOverflow) && scale > 0.7f) scale *= 0.92f
        },
    )
}
