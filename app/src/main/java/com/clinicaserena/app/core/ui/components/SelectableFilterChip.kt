package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

/**
 * Filtro con marca visible al elegirlo: check y colores invertidos (oscuro con texto blanco en día, claro
 * con texto oscuro en noche); el estado no depende solo del color. `FilterChip` ya publica `selected` en la semántica (TalkBack anuncia «Seleccionado»; para Android es un nodo marcable y marcado).
 */
@Composable
fun SelectableFilterChip(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSerenaColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.chip,
            labelColor = colors.chipText,
            selectedContainerColor = colors.chipSelected,
            selectedLabelColor = colors.chipSelectedText,
            selectedLeadingIconColor = colors.chipSelectedText,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.chipBorder,
            selectedBorderColor = Color.Transparent,
            borderWidth = 0.5.dp,
        ),
        modifier = modifier,
    )
}
