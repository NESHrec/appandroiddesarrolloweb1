# Reglas de R8 para release y qaLocal (isMinifyEnabled = true).
#
# No hacen falta reglas propias: kotlinx.serialization (incluye reglas específicas de R8), Retrofit,
# OkHttp y Navigation publican sus reglas de consumidor, y cubren los DTO @Serializable, las interfaces
# Retrofit con funciones suspend y las rutas tipadas de navegación. Verificado con el build qaLocal
# contra el backend local (login, reserva, expediente, recetas, agenda, odontograma, atención y errores
# tipados) en Android 16 (API 36). La verificación en Android 17 (API 37) está pendiente.
#
# Si una actualización de librerías rompe la serialización en release, agregar aquí la regla mínima y
# documentar el motivo. El mapping.txt de cada release queda en app/build/outputs/mapping/ (ignorado por
# git): guardarlo fuera del repo para poder leer los stack traces.
