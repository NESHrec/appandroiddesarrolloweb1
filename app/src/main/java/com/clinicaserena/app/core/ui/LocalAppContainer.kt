package com.clinicaserena.app.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.clinicaserena.app.AppContainer

/** Contenedor de dependencias para las fábricas de ViewModel en Compose (DI manual). */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer no está disponible")
}
