package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.image.practitionerInitials
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

/**
 * Avatar decorativo de iniciales para personas sin foto (pacientes, en las pantallas del médico):
 * círculo violeta del modo (≥ 7.5:1). No tiene semántica propia: el nombre ya está escrito al lado.
 */
@Composable
fun InitialsAvatar(name: String?, size: Dp, modifier: Modifier = Modifier) {
    val tone = LocalSerenaColors.current.avatarViolet
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(tone.background).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val initials = name?.let(::practitionerInitials)
        if (initials != null) {
            // Tamaño fijo respecto del círculo: con letra grande del sistema no se desborda.
            Text(
                initials,
                color = tone.content,
                fontSize = (size.value * 0.38f / LocalDensity.current.fontScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = tone.content, modifier = Modifier.size(size * 0.5f))
        }
    }
}
