package com.clinicaserena.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta del rediseño «vivo» (CLAUDE.md, regla 11). Contraste comprobado: texto ≥ 4.5:1 y
// íconos ≥ 3:1 sobre su fondo, en día y en noche (también las pastillas compuestas sobre tarjeta y fondo).

// Degradados (iguales en día y noche, siempre con texto blanco: 5.2–5.7:1).
val GradienteInicio = Color(0xFF2563EB)
val GradienteFin = Color(0xFF7C3AED)
val GradienteAltInicio = Color(0xFF0F7395)
val GradienteAltFin = Color(0xFF2563EB)
// Check de «Lista para documentar»: verde → turquesa oscuros, para que el ícono blanco tenga ≥ 3.3:1.
val GradienteListoInicio = Color(0xFF16A34A)
val GradienteListoFin = Color(0xFF0D9488)

// ---- Día
val DiaFondo = Color(0xFFF4F6FB)
val DiaTarjeta = Color(0xFFFFFFFF)
val DiaTexto = Color(0xFF141B2D)
val DiaTextoSecundario = Color(0xFF5A6478)
val DiaChevron = Color(0xFF8A94A6)
val DiaSeparador = Color(0xFFE3E7EF)
val DiaBordeCampo = Color(0xFFC3CAD8)
val DiaEnlace = Color(0xFF2563EB)
val DiaSecundarioTexto = Color(0xFF1E3A8A)
val DiaPista = Color(0xFFE4E8F2)
val DiaSegmentoTexto = Color(0xFF374151)
val DiaBarraPestanas = Color(0xFF141B2D)
val DiaPestanaIcono = Color(0xFF818AA1)
val DiaPestanaEtiqueta = Color(0xFFA3ADC2)
val DiaError = Color(0xFFBE123C)

// ---- Noche
val NocheFondo = Color(0xFF0B1020)
val NocheTarjeta = Color(0xFF161D2E)
val NocheSeparador = Color(0xFF252E42)
val NocheTexto = Color(0xFFEEF2FA)
val NocheTextoSecundario = Color(0xFFA3ADC2)
val NocheChevron = Color(0xFF6B7690)
val NocheEnlace = Color(0xFF60A5FA)
val NocheSecundarioTexto = Color(0xFFBFDBFE)
val NochePista = Color(0xFF1C2436)
val NocheSegmentoTexto = Color(0xFFC7CEDC)
val NocheBarraPestanas = Color(0xFF1A2234)
val NochePestanaIcono = Color(0xFF8C97AE)
val NocheBordeCampo = Color(0xFF3A4560)

// Manchas del fondo (alfa bajo).
val ManchaTurquesa = Color(0xFF0F766E)
val ManchaAzul = Color(0xFF1D4ED8)
val ManchaVioleta = Color(0xFF6D28D9)

// Colores base de los estados (el punto de las pastillas, igual en día y noche).
val EstadoAmbar = Color(0xFFF59E0B)
val EstadoVerde = Color(0xFF22C55E)
val EstadoTurquesa = Color(0xFF14B8A6)
val EstadoRosa = Color(0xFFEC4899)
val EstadoVioleta = Color(0xFF8B5CF6)
val EstadoAzul = Color(0xFF3B82F6)
