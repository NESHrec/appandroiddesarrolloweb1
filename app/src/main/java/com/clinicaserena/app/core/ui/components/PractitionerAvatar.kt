package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.image.practitionerInitials
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors

/**
 * Foto pública del profesional, o sus iniciales mientras carga, si no tiene foto o sin conexión. Con foto,
 * TalkBack lee «Foto de {nombre}»; las iniciales son decorativas porque el nombre está en la misma fila.
 */
@Composable
fun PractitionerAvatar(name: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val loader = LocalAppContainer.current.practitionerPhotoLoader
    var image by remember(photoUrl) { mutableStateOf(loader.cached(photoUrl)) }
    LaunchedEffect(photoUrl) {
        if (image == null && photoUrl != null) image = loader.load(photoUrl)
    }
    val loaded = image
    val tone = LocalSerenaColors.current.avatarBlue
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.background)
            .testTag(if (loaded != null) "practitioner_avatar_photo" else "practitioner_avatar_initials"),
        contentAlignment = Alignment.Center,
    ) {
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = stringResource(R.string.foto_de_profesional, name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val initials = practitionerInitials(name)
            if (initials != null) {
                // Tamaño fijo respecto del círculo: con letra grande del sistema no se desborda.
                val fontSize = (size.value * 0.38f / LocalDensity.current.fontScale).sp
                Text(
                    initials,
                    color = tone.content,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = tone.content,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    }
}
