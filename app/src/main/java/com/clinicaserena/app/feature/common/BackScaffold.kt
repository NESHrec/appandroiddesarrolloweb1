package com.clinicaserena.app.feature.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.clinicaserena.app.core.ui.components.CenteredBackBar

/** Pantalla secundaria con barra centrada («‹ Atrás» y el título) y, si hace falta, una barra de acción fija abajo. */
@Composable
fun BackScaffold(
    title: String,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        // Transparente: se ve el fondo con manchas de la app (SerenaBackground).
        containerColor = Color.Transparent,
        topBar = { CenteredBackBar(title = title, onBack = onBack) },
        bottomBar = bottomBar,
        content = content,
    )
}
