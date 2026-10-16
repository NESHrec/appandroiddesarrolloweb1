package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.clinicaserena.app.R

/**
 * Filtro con marca visible al elegirlo: el estado no depende solo del color. `FilterChip` ya publica
 * `selected` en la semántica (TalkBack anuncia «Seleccionado»; para Android es un nodo marcable y marcado).
 */
@Composable
fun SelectableFilterChip(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        modifier = modifier,
    )
}
