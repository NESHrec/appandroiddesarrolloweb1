# Matriz endpoint → pantalla → rol → errores

Verificada contra el backend `Backproyectodesarolloodonto1`, rama `desarrollo`, commit **`7fa072d`**. Para cada fila se revisaron:
- **Ruta y método:** el controlador.
- **Rol:** `config/SecurityConfig.java`.
- **Códigos de error:** los `ApiException` de cada servicio.

Todas las rutas cuelgan de `/api/v1`.

**Cómo maneja la app los errores.** Cada respuesta se convierte en un `ApiResult` tipado (`core/network/ErrorMapper.kt`) y conserva el `code` de Spring:
- **401:** `AUTHENTICATION_FAILED` cuenta como credenciales inválidas; cualquier otro 401 es una sesión vencida y lleva a volver a iniciar sesión.
- **403, 404 y 409:** se tipan conservando su `code`.
- **400 `VALIDATION_ERROR`:** muestra los errores por campo.
- **Otros 4xx:** se tratan como `BadRequest` con su `code`.
- **5xx, red e inesperados:** errores sin código; en las escrituras, la app consulta el estado real antes de permitir reintentar.

La columna «Errores manejados» lista los códigos que tienen un mensaje o un comportamiento propio en la pantalla. Los demás usan el mensaje genérico de su tipo (`core/ui/UserMessages.kt`).

**Verificado en vivo** indica en qué fase se recorrió el endpoint contra Spring real (B1–B3 paciente, C1–C3 médico, E1a build minificado `qaLocal`).

## Acceso y sesión

| Endpoint | Método | Rol | Pantalla Android | Errores manejados | Verificado en vivo |
|---|---|---|---|---|---|
| `/auth/login-unified` | POST | Público | Login (`LoginScreen`) | 401 `AUTHENTICATION_FAILED`: un único mensaje para credenciales incorrectas, cuenta sin verificar o bloqueo de 60 s tras 5 fallos. Rol ADMIN o RECEPCION: mensaje y logout inmediato. Red o 5xx: «Sin conexión» o «Servidor no disponible» | B1, E1a |
| `/auth/me` | GET | PACIENTE | Sesión (`SessionManager`): al iniciar sesión y al restaurarla | 401: limpia la sesión y vuelve al login. Red o 5xx al restaurar: pantalla de reintento, sin borrar la sesión guardada | B1 |
| `/auth/logout` | POST | PACIENTE | Perfil del paciente → Cerrar sesión | 401: igual cierra localmente. Red: aviso «cerrada solo en este teléfono» | B1, E1a |
| `/staff/auth/me` | GET | ADMIN, RECEPCION, MEDICO | Sesión (`SessionManager`) y Perfil del médico | 401: login. ADMIN o RECEPCION: fuera de alcance, logout inmediato | C1, E1a |
| `/staff/auth/logout` | POST | ADMIN, RECEPCION, MEDICO | Perfil del médico → Cerrar sesión | Igual que el logout del paciente | C1, E1a |
| `/auth/register` | POST | Público | Crear cuenta de paciente (`RegisterScreen`) | 400 `VALIDATION_ERROR` por campo. La verificación del correo se completa en la web | B1 |
| `/auth/resend-verification` | POST | Público | Reenviar correo de verificación (`EmailRequestScreen`) | Respuesta neutra (no revela si el correo existe); errores genéricos | B1 |
| `/auth/password-recovery` | POST | Público | ¿Olvidaste tu contraseña? (`EmailRequestScreen`) | Respuesta neutra; el restablecimiento se completa en la web | B1 |
| `/health` | GET | Público | Diagnóstico (solo debug) | Red o 5xx: «Servidor no disponible» | A2 |

## Paciente

| Endpoint | Método | Rol | Pantalla Android | Errores manejados | Verificado en vivo |
|---|---|---|---|---|---|
| `/especialidades` | GET | Público | Reserva → Elige la especialidad | Genéricos y lista vacía | B2, E1a |
| `/medicos?specialtyId=` | GET | Público | Reserva → Elige el médico (solo médicos activos desde V15) | Genéricos y lista vacía | B2, E1a |
| `/medicos/{id}/disponibilidad?from&to` | GET | Público | Reserva → Elige el horario (sin bloques retirados desde V14) | `PRACTITIONER_NOT_FOUND`: el médico ya no está disponible. `INVALID_DATE_RANGE`: genérico. Sin bloques: estado vacío | B2, E1a |
| `/citas` | POST | PACIENTE | Reserva → Confirmar | `SLOT_NOT_AVAILABLE`, `SLOT_NOT_FOUND` y `SLOT_IN_PAST`: el horario ya no está disponible, se refresca la disponibilidad y no se muestra éxito. `SLOT_PRACTITIONER_MISMATCH`, `SPECIALTY_PRACTITIONER_MISMATCH`, `PRACTITIONER_NOT_FOUND`, `SPECIALTY_NOT_FOUND`, `SCHEDULED_AT_INVALID` y `NOTES_TOO_LONG` tienen mensaje propio. Red o 5xx: consulta «Mis citas» antes de reintentar | B2, E1a |
| `/pacientes/me/citas` | GET | PACIENTE | Inicio (próxima cita), Citas (próximas e historial con filtros) y detalle de la cita, armado con los datos de la lista | Genéricos; vacío por filtro | B2, E1a |
| `/pacientes/me/expediente` | GET | PACIENTE | Mi expediente → Resumen y Atenciones (incluidas las adendas) | Sin expediente todavía (200 con `recordId` nulo): estado vacío. Genéricos | B3, C3, E1a |
| `/pacientes/me/chequeos` | GET | PACIENTE | Mi expediente → Seguimientos | Genéricos; vacío | B3 |
| `/pacientes/me/recetas` | GET | PACIENTE | Recetas | Genéricos; vacío | B3, E1a |
| `/pacientes/me/perfil` | GET | PACIENTE | Mi perfil | Genéricos | B3 |
| `/pacientes/me/perfil` | PATCH | PACIENTE | Mi perfil → Editar nombre | 400 `VALIDATION_ERROR` por campo. 401: conserva el cambio y reintenta tras el re-login | B3 |
| `/pacientes/me/dispositivos-push` | PUT | PACIENTE | Sin pantalla propia: tras el login, al renovar el token y al volver a la app. Estado en Inicio (invitación) y Mi perfil → Recordatorios de citas. Cuerpo `{token, platform:"ANDROID"}`, 204 | 409 `PUSH_NOT_ENABLED` y 400 `PUSH_TOKEN_NOT_SIMULATED`: «No disponibles», sin error. 401: espera el re-login y vuelve a registrar. Red o 5xx: «No se pudieron activar» con Reintentar. Solo muestra «Activados» tras el 204 | FCM (backend `f483454`) |
| `/pacientes/me/dispositivos-push/retiro` | POST | PACIENTE | Cerrar sesión (antes de `/auth/logout`) y al revocar el permiso de notificaciones. Cuerpo `{token}`, 204 | De mejor esfuerzo (5 s): `/auth/logout` también retira el dispositivo de esa sesión y después se borra el token en FCM | FCM (backend `f483454`) |

## Médico

Todas las rutas `/medico/**` exigen el rol MEDICO **y** que la cuenta esté vinculada a un profesional. Sin vinculación responden **403 `PRACTITIONER_LINK_REQUIRED`**, y la app muestra «Tu cuenta aún no está vinculada» con un botón Reintentar.

Todo recurso de otro médico responde **404**, nunca 403: `APPOINTMENT_NOT_FOUND`, o `PATIENT_NOT_FOUND` en el odontograma. Es lo mismo que si el recurso no existiera.

| Endpoint | Método | Rol | Pantalla Android | Errores manejados | Verificado en vivo |
|---|---|---|---|---|---|
| `/medico/citas?from&to&status&limit` | GET | MEDICO | Inicio (conteo de pacientes distintos y citas listas) y Agenda (rangos y estados) | `PRACTITIONER_LINK_REQUIRED`. `LIMIT_INVALID`, `DATE_RANGE_INVALID` e `INVALID_PARAMETER`: genérico. Si llegan 100 citas (`limit` máximo), aviso de «resultado posiblemente incompleto» | C1, E1a |
| `/medico/citas/{id}` | GET | MEDICO | Detalle de la cita: motivos de bloqueo (`attentionBlockers`) y atención con sus adendas | 404 `APPOINTMENT_NOT_FOUND`: «No encontramos esta cita en tu agenda» | C1, C3, E1a |
| `/medico/citas/{id}/expediente` | GET | MEDICO | Ver expediente | 404 `APPOINTMENT_NOT_FOUND`. 409 `PATIENT_RECORD_UNAVAILABLE`: genérico | C2, E1a |
| `/medico/citas/{id}/atencion` | POST | MEDICO | Registrar atención (formulario → confirmación) | 409, en el orden en que los evalúa el servidor: `ATTENTION_ALREADY_RECORDED` (ofrece ver la atención existente), `APPOINTMENT_NOT_DOCUMENTABLE`, `APPOINTMENT_NOT_STARTED`, `ARRIVAL_NOT_REGISTERED`, `PATIENT_RECORD_UNAVAILABLE`, cada uno con su mensaje. 400 `VALIDATION_ERROR` por campo (`prescription[i].campo`). 401: re-login y un solo reintento. Red o 5xx: consulta la cita antes de reenviar | C2, E1a |
| `/medico/citas/{id}/expediente/perfil` | POST | MEDICO | Nueva versión del perfil clínico | 400 `CLINICAL_PROFILE_EMPTY`: vuelve al formulario. 401: re-login y un reintento. Red o 5xx: consulta el expediente | C2 |
| `/medico/citas/{id}/atenciones/{attentionId}/adendas` | POST | MEDICO | Agregar adenda (desde el detalle de la cita) | 404 `ATTENTION_NOT_FOUND` o `APPOINTMENT_NOT_FOUND`: «No encontramos esta atención». 400 `VALIDATION_ERROR` por campo. 401: re-login y un reintento. Red o 5xx: consulta la cita | C3 |
| `/medico/pacientes/{patientId}/odontograma` | GET | MEDICO | Odontograma: historial agrupado por pieza y superficie, nunca como «estado actual» | 404 `PATIENT_NOT_FOUND`: «No encontramos a este paciente en tu agenda» | C3, E1a |
| `/medico/citas/{id}/odontograma` | POST | MEDICO | Nueva observación (pieza FDI, superficie y texto) | 409 `APPOINTMENT_NOT_DOCUMENTABLE` (con la atención ya registrada, el odontograma de esa cita queda cerrado), `APPOINTMENT_NOT_STARTED`, `ARRIVAL_NOT_REGISTERED` y `PATIENT_RECORD_UNAVAILABLE`. 400 `TOOTH_INVALID`, `SURFACE_INVALID` y `OBSERVATION_INVALID` marcan su campo. 404 `PATIENT_NOT_FOUND`. Red o 5xx: consulta el historial | C3, E1a |

## Endpoints del backend que la app no usa

Quedan fuera del alcance de la app (ADMIN y RECEPCION), o existen pero no se integraron:

| Endpoint | Rol | Nota |
|---|---|---|
| `/auth/verify-email`, `/auth/password-reset` | Público | Se completan en la web, con el enlace del correo (`AUTH_PUBLIC_WEB_URL`) |
| `/auth/login`, `/staff/auth/login` | Público | La app usa `login-unified` |
| `/medicos/{id}/foto` (GET) | Público | Nuevo en `7fa072d`. Posible mejora: mostrar la foto del médico al reservar |
| `/staff/medico/horarios` (GET, POST) y `/staff/medico/horarios/{blockId}` (PATCH, DELETE) | MEDICO | El médico crea, edita y retira bloques desde la web. PATCH y DELETE son nuevos en `7fa072d` |
| `/staff/accounts/**`, `/staff/especialidades/**`, `/staff/medicos/**`, `/staff/audit-events` | ADMIN | Administración |
| `/staff/agenda`, `/staff/agenda/{id}/arrival`, `/staff/patients/**`, `/staff/patient-link-requests/*`, `/staff/billing/**` | RECEPCION (y ADMIN en algunas lecturas) | Recepción: agenda, llegada, pacientes administrativos, vinculación y cobros |

## Dependencias de backend que faltan

| Necesidad | Estado |
|---|---|
| Notificaciones push | Registro y retiro existen en `feature/firebase-push-notifications` (`f483454`), aún sin fusionar en `desarrollo`. No hay envío inmediato: solo recordatorios programados 24 h antes |
| Paginación en `/medico/citas` | No existe; `limit` máximo 100. La app avisa si el resultado puede estar incompleto |
| Métricas del médico (pacientes atendidos, etc.) | No hay endpoint; el conteo se calcula en el cliente sobre `/medico/citas` |
| Listado o búsqueda de pacientes propios del médico (`/medico/pacientes`) | No existe; al paciente se llega por una cita |
| Detalle de cita para el paciente | No existe; se arma con los datos de `/pacientes/me/citas` |
| Registrar la llegada o cambiar el estado de la cita desde el médico | Solo lo hace recepción |
| Autor de cada observación de odontograma | La respuesta trae solo IDs; la app muestra la fecha |
| Recuperación de contraseña del personal y refresh token | No existen; ante un 401, la app pide iniciar sesión de nuevo |
