# Informe final — App Android de Clínica Serena

Estado al 16/10/2026 (Fase E2). Este informe resume qué hace la app, cómo se construye, cómo se probó y qué queda pendiente. No contiene credenciales, tokens ni datos clínicos; las evidencias (capturas y salidas) se guardaron localmente y no se versionan.

Documentos relacionados: [`README.md`](../README.md) (instalación y solución de problemas) y [`docs/matriz-endpoints.md`](matriz-endpoints.md) (endpoint → pantalla → rol → errores).

## 1. Resumen y alcance

- App Android nativa para los roles **PACIENTE** y **MEDICO**, que consume la API Spring Boot de Clínica Serena (`/api/v1`). No tiene base de datos clínica propia ni usa el BFF de Next.js.
- **Paciente:** registro (la verificación de correo y la recuperación de contraseña se completan en la web), inicio de sesión, reserva de citas por especialidad, médico y horario, «Mis citas» con filtros y detalle, recetas, expediente (atenciones, adendas, perfil clínico y seguimientos) y edición del perfil.
- **Médico:** inicio con el conteo de pacientes por período, agenda, detalle de la cita con sus bloqueos, registro de la atención (diagnóstico, receta y seguimiento), perfil clínico versionado, adendas y odontograma (observaciones append-only, mostradas como historial por pieza y superficie).
- **Fuera de alcance:** ADMIN y RECEPCION. Si una de esas cuentas inicia sesión, la app muestra un mensaje claro y cierra la sesión de inmediato.

## 2. Arquitectura

- Kotlin y Jetpack Compose con MVVM: UI → ViewModel → repositorio → cliente API (Retrofit/OkHttp). Corrutinas y Flow. Inyección manual (`AppContainer`).
- Capas: `core/` (network, security, ui, image), `data/` (DTO y repositorios), `domain/` (modelos de pantalla) y `feature/*`. Los DTO de la API están separados de los modelos de pantalla.
- **Sesión:** login unificado (`POST /auth/login-unified`). Según `accountType`, la app usa `/auth/*` (paciente) o `/staff/auth/*` (personal). El token Bearer es opaco, tiene un TTL de 1800 s y no hay refresh. Se guarda en DataStore, cifrado con una llave AES-GCM de Android Keystore.
- **401 → re-login:** los formularios del médico (atención, perfil clínico, adenda y observación) conservan el borrador solo en memoria y lo reenvían una única vez después de volver a iniciar sesión.
- **Errores tipados** según `ApiError {status, code, message, fieldErrors}`: 401, 403, 404 y 409 se distinguen por su `code`. Toda pantalla con datos maneja carga, vacío, error recuperable, sesión vencida, permiso denegado y éxito, con textos en español.
- **Fechas:** `OffsetDateTime` ISO 8601, mostradas en America/Guatemala. Al reservar se reenvía el `startAt` del bloque tal cual llegó.
- **Fotos de profesionales:** se descargan con un cliente HTTP propio sin Bearer. Solo se aceptan del mismo origen que la API, PNG o JPEG de hasta 2 MB y 4096 px. Se guardan solo en memoria y se borran al cerrar o vencer la sesión. Sin foto, se muestran las iniciales.

## 3. Configuración por build

| Build | URL de la API | Minificación | Firma | Uso |
|---|---|---|---|---|
| `debug` | `http://10.0.2.2:8080/api/v1/` (cambia con `-PclinicaSerena.debugApiBaseUrl`) | No | Llave debug | Desarrollo y demo |
| `qaLocal` | Igual que debug | R8 | Llave debug, appId `.qa` | Probar el build minificado contra el backend local |
| `release` | `-PclinicaSerena.releaseApiBaseUrl` (HTTPS obligatorio, validado) | R8 | Externa (`.properties` fuera del repo) | Entrega |

- El HTTP sin cifrar solo se permite en debug y qaLocal, y solo hacia `10.0.2.2` y `localhost`.
- En un teléfono físico se usa `scripts/dev-reverse.sh` (`adb reverse tcp:8080 tcp:8080`) con la URL `http://localhost:8080/api/v1/`.
- compileSdk 37, targetSdk 36 y minSdk 26. Subir targetSdk a 37 queda pendiente (ver la sección 8).

## 4. Endpoints usados

El detalle completo está en [`docs/matriz-endpoints.md`](matriz-endpoints.md). En resumen:

- **Autenticación:** `login-unified`, y `me` y `logout` en sus variantes de paciente y de personal. Registro de paciente.
- **Paciente:** catálogo (especialidades, médicos y disponibilidad), reserva, `/pacientes/me` (perfil, citas, recetas y expediente).
- **Médico:** `/medico/citas` (agenda y conteo, con `limit` máximo de 100), detalle, expediente, atención, perfil clínico, adendas y odontograma.
- **Público:** `GET /medicos/{id}/foto` (la foto, sin Bearer).
- **Aún no consumidos por la app:** los endpoints push del backend (`PUT /pacientes/me/dispositivos-push` y `POST /pacientes/me/dispositivos-push/retiro`, de la rama `feature/notificaciones-push`). Se integran en la D2.

## 5. Pruebas y resultados

### Pruebas automáticas (rama de integración: `desarrollo` + PR #1 + PR #2)
- `testDebugUnitTest`: **197 pruebas unitarias**, todas OK.
- `connectedDebugAndroidTest`: **6 pruebas instrumentadas** en el emulador de API 36, todas OK.
- `lintDebug` y `lintRelease`: sin errores. Solo quedan avisos de versión más nueva disponible (Kotlin 2.4.21) y de targetSdk anterior al último.
- `assembleDebug`, `assembleQaLocal` y `assembleRelease`: compilan.
- Backend (`feature/notificaciones-push`, `d4a39cf`): **155 pruebas**, todas OK (ejecutadas en la Fase D1).

### Prueba integral en vivo (E2)
Se probó contra el backend real en el perfil dev, con Mailpit y la web local para verificar el correo, en el emulador Pixel 9 Pro XL de API 36. Se usaron datos sintéticos nuevos (una paciente registrada desde la app y bloques futuros del médico de prueba). ★ = repetido en el build qaLocal (R8). Todas las evidencias son capturas o salidas resumidas locales, no versionadas.

| # | Paso | Resultado |
|---|---|---|
| P1 | Registro desde la app; correo en Mailpit | OK |
| P2 | Verificación con el enlace en la web local. Antes de verificar, el login da el 401 genérico | OK |
| P3 ★ | Login e Inicio | OK |
| P4 ★ | Reserva: especialidad → médico con foto → horario → confirmación con foto → éxito; la cita aparece en «Mis citas» y en la API | OK |
| P5 | Otra cuenta reserva el mismo bloque con la confirmación abierta → «Ese horario ya no está disponible», sin éxito falso y con una sola cita en el bloque | OK |
| P6 ★ | Filtros de «Mis citas» y detalle con foto | OK |
| P7 | Recetas, expediente con atención, adenda y perfil, y seguimiento | OK |
| P8 | Editar el nombre (200), logout y la app reabre sin sesión | OK |
| M1 ★ | Conteo de pacientes por período igual al calculado sobre `/medico/citas` | OK |
| M2 ★ | Agenda con rangos y filtros | OK |
| M3 ★ | Detalle: primero los bloqueos, luego «Lista para documentar» | OK |
| M4 | Observación de odontograma antes de la atención; historial por pieza y superficie | OK |
| M5 | Atención con 2 medicamentos → avisos → registrar; la cita pasa a COMPLETADA | OK |
| M6 | Nueva versión del perfil clínico (2 → 3) | OK |
| M7 | Adenda a la atención, visible para la paciente | OK |
| M8 ★ | Odontograma bloqueado después de la atención, con su motivo | OK |
| T1 | Re-login con borrador: «Simular sesión vencida» → re-login → borrador intacto → exactamente 1 registro | OK |
| T2 | Descartar borrador con la flecha, el botón y el gesto atrás: diálogo y nada guardado | OK |
| T3 | Aislamiento entre pacientes: solo ve lo propio; un paciente recibe 403 en `/medico/**` | OK |
| T4 | Aislamiento entre médicos: el otro médico recibe 404 en 6 recursos ajenos, sin efectos en la BD | OK |
| T5 ★ | Sin red: «No hay conexión con el servidor» y Reintentar; se recupera | OK |
| T6 ★ | ADMIN y RECEPCION: mensaje de rol no soportado y su sesión queda revocada | OK |
| T7 | Privacidad: en todo el logcat de la prueba (unas 177 000 líneas, ambos builds) hay 0 Bearer, `accessToken`, `Authorization`, contraseñas, tokens de verificación o textos clínicos sintéticos; buffer de crashes vacío. Tras el logout, en disco solo quedan la sesión vacía (0 bytes) y `profileInstalled` | OK |
| T8 | Accesibilidad: 720×1280, 320 dpi y letra 1.5× en Inicio, reserva con foto, detalle, atención y odontograma | OK, con 1 observación (sección 7) |

Las fases B a E1 ya se habían verificado en vivo en su momento: bloqueo tras 5 intentos de login, aislamiento con dos médicos, reglas del odontograma, etc. Los hallazgos de cada fase están en `CLAUDE.md`.

## 6. Seguridad y privacidad

- El Bearer se guarda cifrado con Android Keystore. Nunca se escribe en logs, errores ni analytics, igual que las contraseñas y los tokens de verificación o recuperación (comprobado en T7).
- Nada clínico se guarda en disco: expedientes, diagnósticos, recetas y odontogramas viven solo en memoria y se limpian al cerrar sesión.
- La app no autoriza con IDs de la UI; Spring valida cada operación. El aislamiento se comprobó en el servidor (T3 y T4): un recurso ajeno da 404 sin efectos.
- Las fotos de profesionales se piden sin Bearer, solo al mismo origen y solo se guardan en memoria.
- Release: HTTPS obligatorio, R8 y firma con una llave fuera del repo.

## 7. Limitaciones y faltantes del backend

- **Detalle de cita del paciente:** no hay endpoint de detalle; se construye con los datos de la lista. Agregar `practitionerName`, `specialtyName` y `endsAt` a `CitaDto` lo haría más simple.
- No se puede **cancelar ni reprogramar** una cita desde la app (no hay endpoint para el paciente).
- `/medico/citas` **no tiene paginación** (`limit` máximo de 100): si llegan 100 resultados, la app avisa que el conteo puede estar incompleto.
- **Sin refresh token:** al vencer la sesión (30 min) hay que volver a iniciar sesión.
- **Recuperación de contraseña del personal:** no existe; solo la del paciente, por la web.
- Las observaciones del odontograma **no traen su autor**.
- **Recordatorios push:** una cita reservada con menos de unas 22 h de anticipación no recibe el recordatorio de 24 h.
- **Accesibilidad (observación de E2):** el filtro seleccionado en «Mis citas» (por ejemplo, «Esta semana») indica su estado solo con el color. Conviene agregar un ícono de marca o un texto.

## 8. Pendientes

- **D2:** notificaciones push en la app con Firebase (FCM). Ver la sección 11.
- **targetSdk 37:** requiere probar en Android 17. Con el target 37, Android 17 exige `ACCESS_LOCAL_NETWORK` para la red local y activa Certificate Transparency.
- **Release:** custodia y respaldo de la llave de firma definitiva, y URL HTTPS de producción.
- **Kotlin 2.4.21** (lint avisa que hay una versión más nueva).

## 9. Instalar el APK de depuración (demo)

Los APK de demo se generan desde la rama de integración y se entregan **fuera del repo**, con un archivo de sumas SHA-256. No se versionan. Hay dos variantes:

- **Emulador:** `./gradlew assembleDebug` (URL `10.0.2.2`).
- **Teléfono físico:** `./gradlew assembleDebug -PclinicaSerena.debugApiBaseUrl=http://localhost:8080/api/v1/`.

Para instalar en un teléfono:
1. Activar la depuración USB y aceptar la huella del equipo.
2. `adb install -r <apk>`. Si responde `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (otra versión firmada con otra llave debug), desinstalar primero con `adb uninstall com.clinicaserena.app`.
3. Con el backend arriba, correr `scripts/dev-reverse.sh`.

Son APK de depuración: solo hablan con un backend local y no sirven para distribución.

## 10. Cómo hacer la demo

**Antes de empezar:**
1. Levantar el backend (`/api/v1/health` → `UP`) y abrir Mailpit (`localhost:8025`).
2. El médico debe tener **bloques futuros** creados (un bloque desaparece de la disponibilidad en cuanto inicia).
3. Tener a mano las cuentas de prueba: un paciente verificado y un médico vinculado a un profesional. Las credenciales están en el archivo local del equipo; nunca se muestran en pantalla ni se proyectan.

**Recorrido recomendado (unos 10 minutos):**
1. **Paciente:** login → Inicio → reservar (especialidad → médico con foto → horario → confirmar) → ver la cita en «Mis citas» y su detalle. Cerrar sesión.
2. **Médico:** login → Inicio (conteo por período) → Agenda → detalle de la cita del paciente.
   - Para documentar, la cita debe haber iniciado y tener la llegada registrada por recepción. Se puede usar una cita preparada de antemano.
   - Agregar una observación de odontograma → registrar la atención con una receta → ver que el odontograma queda bloqueado. Cerrar sesión.
3. **Paciente otra vez:** «Recetas» muestra la **receta nueva**, y el expediente muestra la atención.

**Si el emulador pierde la red** («No hay conexión con el servidor» con el backend arriba):
1. Activar y desactivar el **modo avión** en el emulador (o `adb shell cmd connectivity airplane-mode enable` y luego `disable`) y tocar «Reintentar».
2. Si sigue sin red: en el Device Manager de Android Studio, **Cold Boot Now** del emulador.
3. Comprobar desde la Mac que `curl -s localhost:8080/api/v1/health` responde `UP`.

## 11. Estado de los PR y cierre

**Al 16/10/2026, ninguno está fusionado en `desarrollo`:**
- **App PR #1** `chore/flujo-ramas-y-acuerdos`: flujo de ramas y acuerdos del equipo en `CLAUDE.md`.
- **App PR #2** `feature/fotos-medicos`: fotos públicas de profesionales en la reserva y en el detalle de la cita.
- **Backend** `feature/notificaciones-push` (D1, migración V17): dispositivos push y recordatorios de cita, con la tarea apagada por defecto y solo un envío simulado en dev/test.
- **Este informe:** rama `chore/informe-final`, con su PR hacia `desarrollo` cuando se apruebe.

Los dos PR de la app no comparten archivos, y su combinación se probó completa en E2.

**Para cerrar después de la D2:**
1. **Fusiones:** que el equipo apruebe y fusione los PR de la app, el del backend y el de este informe.
2. **D2 en la app:**
   - Firebase con la cuenta del equipo, `google-services` y FCM.
   - Pedir el permiso de notificaciones (Android 13+).
   - Registrar el token al iniciar sesión y retirarlo al cerrarla.
   - Manejar 409 `PUSH_NOT_ENABLED`.
   - Abrir la cita desde la notificación.
3. **D2 en el backend:** el emisor push real, con la credencial por variable de entorno.
4. **Re-prueba integral** con push real en un dispositivo, y agregar el push a la matriz y a este informe.
5. **targetSdk 37** con prueba en Android 17.
6. **Release firmado:** llave definitiva custodiada, URL HTTPS con certificado de una autoridad pública, y el APK y su `mapping.txt` guardados fuera del repo.
7. **Backend:** campos del detalle de cita del paciente, si el equipo lo aprueba.
8. **Fecha de entrega:** pendiente de confirmar.
