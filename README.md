# Clínica Serena — App Android (paciente y médico)

App Android nativa (Kotlin y Jetpack Compose) para los roles **PACIENTE** y **MEDICO** de Clínica Serena. Todos los datos vienen de la API Spring Boot del proyecto; la app no tiene base de datos clínica propia ni guarda en disco expedientes, recetas ni odontogramas. ADMIN y RECEPCION quedan fuera de alcance (usan la web).

- **Paciente:** registro e inicio de sesión, reserva de citas, «Mis citas», expediente (con adendas), seguimientos, recetas y perfil.
- **Médico:** agenda propia y conteo de pacientes por período, detalle de la cita, expediente, registro de la atención con receta, nueva versión del perfil clínico, adendas y odontograma (historial de observaciones).

Documentos relacionados:
- [`docs/matriz-endpoints.md`](docs/matriz-endpoints.md): endpoint → método → rol → pantalla → errores, verificada contra el backend.
- [`CLAUDE.md`](CLAUDE.md): reglas del proyecto, decisiones y estado del entorno.

## Requisitos

| Herramienta | Versión usada |
|---|---|
| JDK | 21 (Temurin). Gradle y el backend lo usan |
| Android SDK | Platform 37 (`compileSdk 37`), build-tools 36, platform-tools (adb) |
| Android Studio | Opcional (versión con soporte de AGP 9.4) |
| Docker Desktop | Para el Postgres y el Mailpit del backend |
| Backend | `Backproyectodesarolloodonto1`, rama `desarrollo` |
| Emulador | AVD con Android 16 (API 36), por ejemplo Pixel 9 Pro XL |

Versiones de la app: AGP 9.4.1, Kotlin 2.4.20, Gradle 9.8.1, Compose BOM 2026.09.00, Retrofit 3.0.0, OkHttp 5.5.0, kotlinx.serialization 1.11.0, Firebase BoM 35.1.0 (`firebase-messaging` 26.0.1) y plugin `google-services` 4.5.0; `minSdk 26`, `targetSdk 36` y `compileSdk 37`.

> **targetSdk sigue en 36.** Subir a 37 (Android 17) queda pendiente hasta probar la app en un dispositivo con Android 17, por dos cambios que afectan a la app:
> - **Red local:** Android 17 exige el permiso `ACCESS_LOCAL_NETWORK`, y `10.0.0.0/8` (que incluye `10.0.2.2`, la dirección del emulador hacia la computadora) cuenta como red local.
> - **Certificate Transparency:** queda activada por defecto.
>
> El emulador API 37 no arrancó en la Mac de desarrollo (más detalle en CLAUDE.md). La alternativa es probarlo en un teléfono físico con Android 17, usando `adb reverse`.

Gradle encuentra el SDK por la variable `ANDROID_HOME` o por `local.properties` (`sdk.dir=…`, ignorado por git):

```bash
export ANDROID_HOME=~/Library/Android/sdk
```

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Estructura

```
app/src/main/java/com/clinicaserena/app/
  core/      network (Retrofit, errores tipados), security (sesión cifrada con Keystore), push (FCM), time, ui
  data/      repositorios y DTO por área (auth, catalog, patient, doctor, push)
  domain/    modelos de pantalla
  feature/   pantallas y ViewModels (auth, patient, doctor, clinical, common)
app/src/debug/     registro HTTP (nivel BASIC, sin cuerpos) y herramientas de prueba de sesión
app/src/release/   versiones vacías de lo anterior (release y qaLocal)
app/src/qaLocal/   configuración de red y nombre del build de prueba
scripts/dev-reverse.sh   adb reverse del puerto 8080 en todos los dispositivos
docs/matriz-endpoints.md
```

## 1. Levantar el backend local

> En una Mac con un PostgreSQL propio en el puerto **5432**, el Postgres de Docker del backend debe usar **55432** (`DB_PORT=55432`) para no chocar con él. No detengas ni modifiques el Postgres de la Mac.

Desde la carpeta del backend (rama `desarrollo`, con su `.env` local; no lo edites ni lo subas):

```bash
cd ~/Desktop/Backproyectodesarolloodonto1 && set -a && source .env && set +a && export DB_PORT=55432 JAVA_HOME=$(/usr/libexec/java_home -v 21) && docker compose up -d && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Comprobar que responde (debe decir `{"status":"UP",…}`):

```bash
curl -s localhost:8080/api/v1/health
```

- **Mailpit** (correos de verificación y recuperación): <http://localhost:8025>
- Flyway aplica las migraciones al arrancar. En el commit `7fa072d` la última es V16.

## 2. Compilar y ejecutar

### Tipos de compilación

| Tipo | Para qué | URL de la API | Notas |
|---|---|---|---|
| `debug` | Desarrollo diario | `clinicaSerena.debugApiBaseUrl` (por defecto `http://10.0.2.2:8080/api/v1/`) | HTTP sin TLS permitido solo hacia `10.0.2.2` y `localhost`. Registro HTTP nivel BASIC (sin cuerpos ni cabeceras) y herramientas de prueba de sesión |
| `qaLocal` | Probar el APK **minificado con R8** contra el backend local | Igual que debug | Mismo código que release (sin registro HTTP ni herramientas de depuración, no depurable). Se instala aparte como «Clínica Serena QA» (`com.clinicaserena.app.qa`) y se firma con la llave debug |
| `release` | Entrega | `clinicaSerena.releaseApiBaseUrl` (**HTTPS obligatorio**) | R8 y eliminación de recursos sin uso. Firma desde fuera del repo (ver [Firma](#5-firma-del-release)) |

### En el emulador

Con el backend arriba y el emulador encendido:

```bash
./gradlew installDebug
```

Abre «Clínica Serena» en el emulador. Desde el emulador, `10.0.2.2` es la computadora, así que no hace falta nada más.

Para el build minificado de prueba:

```bash
./gradlew installQaLocal
```

### En un teléfono físico (USB, con `adb reverse`)

Un teléfono no ve `10.0.2.2`, así que se usa `localhost` del teléfono, redirigido a la computadora con `adb reverse`:

1. Activa **Opciones de desarrollador → Depuración USB**, conecta el teléfono y acepta la huella de la computadora. Comprueba que aparece como `device`:
   ```bash
   adb devices
   ```
2. Instala la app apuntando a `localhost`:
   ```bash
   ./gradlew installDebug -PclinicaSerena.debugApiBaseUrl=http://localhost:8080/api/v1/
   ```
   Para no repetir la propiedad, puedes ponerla en `~/.gradle/gradle.properties`, fuera del repo.
3. Crea la redirección del puerto 8080 en todos los dispositivos conectados:
   ```bash
   scripts/dev-reverse.sh
   ```

> **La redirección se pierde** al reconectar el cable, reiniciar el teléfono o el emulador, o reiniciar adb. **Vuelve a ejecutar `scripts/dev-reverse.sh`** en esos casos. Para comprobarla:
> ```bash
> adb reverse --list
> ```
> Las conexiones que la app ya tenía abiertas pueden seguir funcionando unos minutos después de perder la redirección. Para comprobar de verdad si llega al backend, cierra la app por completo y vuelve a abrirla.

El mismo método funciona con el emulador, y es el recomendado para probar Android 17 cuando se suba el targetSdk, porque `localhost` no cuenta como red local.

## 3. Pruebas

| Comando | Qué hace |
|---|---|
| `./gradlew testDebugUnitTest` | Pruebas unitarias en la JVM: ViewModels, validaciones, repositorios con MockWebServer y sesión |
| `./gradlew connectedDebugAndroidTest` | Pruebas instrumentadas (cifrado con Android Keystore). Necesita un emulador o teléfono conectado. **Desinstala la app debug al terminar** |
| `./gradlew lintDebug lintRelease` | Lint |
| `./gradlew assembleDebug assembleQaLocal assembleRelease` | Compila los tres tipos. Los APK quedan en `app/build/outputs/apk/` (ignorado por git) |

Todo junto:

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleQaLocal assembleRelease connectedDebugAndroidTest
```

Los recorridos completos (paciente y médico) se prueban a mano contra el backend real; ver la columna «Verificado en vivo» de la matriz.

## 4. Cuentas de prueba (sin credenciales en el repo)

Las credenciales de prueba **nunca** van al repositorio, a capturas ni a logs. Guárdalas en un archivo local fuera del repo (por ejemplo `~/Desktop/clinica-serena-local/`, con permisos 600).

**Paciente:**
1. En la app, «Crear cuenta de paciente».
2. Abre el correo de verificación en Mailpit (<http://localhost:8025>). El enlace apunta a la web (`AUTH_PUBLIC_WEB_URL`, por defecto `http://localhost:3000/verificar-correo#token=…`).
3. Si la web no está corriendo, copia el valor después de `#token=` y confírmalo por la API. No lo pegues en chats ni lo dejes en el historial:
   ```bash
   curl -s -X POST localhost:8080/api/v1/auth/verify-email -H 'Content-Type: application/json' -d "{\"token\":\"$TOKEN_VERIFICACION\"}"
   ```
   Hasta que se verifica, el login responde con el mismo 401 que unas credenciales incorrectas.

**Primer ADMIN:** se crea una sola vez, con el procedimiento «Provisionamiento controlado del primer administrador» del README del backend (hash BCrypt e inserción en la base local). No hay endpoint para eso.

**MEDICO (y RECEPCION)**, con una sesión ADMIN (token obtenido con `POST /auth/login-unified`, guardado en una variable y nunca impreso):
1. Crear la cuenta. `role` es `MEDICO` o `RECEPCION`; la contraseña debe tener entre 12 y 72 caracteres:
   ```bash
   curl -s -X POST localhost:8080/api/v1/staff/accounts -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d "{\"email\":\"$CORREO\",\"fullName\":\"$NOMBRE\",\"role\":\"MEDICO\",\"password\":\"$CLAVE\"}"
   ```
2. Vincular el médico a un profesional del catálogo (`GET /api/v1/medicos` lista los `id`). Sin vinculación, la app muestra «Tu cuenta aún no está vinculada» (403 `PRACTITIONER_LINK_REQUIRED`):
   ```bash
   curl -s -X PUT localhost:8080/api/v1/staff/accounts/$ID_CUENTA/practitioner -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d "{\"practitionerId\":\"$ID_PROFESIONAL\"}"
   ```

**Datos para probar:**
- El médico crea bloques de horario futuros con `POST /api/v1/staff/medico/horarios` (`{"startAt","endAt"}` en ISO 8601). Un bloque desaparece de la disponibilidad en cuanto empieza.
- El paciente reserva desde la app.
- Para documentar una cita, recepción registra la llegada (`POST /api/v1/staff/agenda/{id}/arrival`) una vez que la cita empezó.
- El odontograma solo acepta observaciones **antes** de registrar la atención de esa cita.

## 5. Firma del release

El release se firma con una keystore que **nunca** está en el repo (`.gitignore` excluye `*.jks`, `*.keystore`, `*.p12`, `signing*.properties` y `/firma/`). Gradle lee un archivo `.properties` **fuera del repo**:

```properties
storeFile=/ruta/fuera/del/repo/clinica-serena.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Su ruta se indica con la propiedad `clinicaSerena.signingProperties` (en la línea de comandos o en `~/.gradle/gradle.properties`) o con la variable de entorno `CLINICA_SERENA_SIGNING_PROPERTIES`:

```bash
./gradlew assembleRelease -PclinicaSerena.signingProperties=$HOME/ruta/firma.properties -PclinicaSerena.releaseApiBaseUrl=https://api.ejemplo.com/api/v1/
```

| Situación | Resultado |
|---|---|
| Sin configurar | El release sale **sin firmar** (`app-release-unsigned.apk`) y Gradle muestra un aviso |
| Archivo inexistente, campo faltante, keystore inexistente, o archivo o keystore dentro del repo | La compilación **falla** con un mensaje que nombra el campo, nunca su valor |
| Configuración completa | `app-release.apk` firmado |

Para comprobar la firma (muestra solo el certificado y su huella):

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

> ⚠️ **Respalda la llave definitiva fuera del repo**, en al menos dos lugares seguros (por ejemplo, un gestor de contraseñas del equipo y un almacenamiento cifrado), junto con sus contraseñas. **Sin esa llave no se pueden publicar actualizaciones con la misma identidad**: Android rechaza instalar una actualización firmada con otra llave, y los usuarios tendrían que desinstalar la app y perder sus datos locales. La llave de prueba del desarrollo local **no** sirve para la entrega. La custodia de la llave definitiva está pendiente de definir.

## 6. URL de producción

```bash
./gradlew assembleRelease -PclinicaSerena.releaseApiBaseUrl=https://api.dominio-real/api/v1/
```

- Debe usar `https`, tener host, no incluir usuario ni contraseña y **terminar en `/`**. Si no, la compilación falla con un mensaje claro.
- El valor por defecto (`gradle.properties`) usa un dominio `.invalid` y Gradle avisa «URL de producción no definida». Así un release de prueba nunca apunta por accidente a un servidor real.
- El release solo admite HTTPS (`network_security_config` sin cleartext).
- Cuando se suba a targetSdk 37, el certificado del servidor deberá venir de una autoridad pública, por la Certificate Transparency que Android 17 activa por defecto.
- **R8:** el `mapping.txt` de cada release queda en `app/build/outputs/mapping/release/`, ignorado por git. Guárdalo fuera del repo junto con el APK entregado para poder leer los stack traces.

## 7. Notificaciones push (Firebase Cloud Messaging)

Recordatorios de cita para el **PACIENTE**: el backend envía uno por cita, entre 24 y 22 horas antes, solo con fecha y hora. La app no tiene un endpoint de envío inmediato ni datos clínicos en la notificación.

**`google-services.json` (no está en git).** El plugin `google-services` lo exige para compilar cualquier variante. Se descarga del proyecto Firebase `clinica-serena-push` (Firebase Console → Configuración del proyecto → Tus apps → Android) y debe traer **dos clientes**: `com.clinicaserena.app` (debug y release) y `com.clinicaserena.app.qa` (qaLocal). Se copia en `app/google-services.json`; `.gitignore` lo excluye junto con `app/src/*/google-services.json`. Ese archivo no contiene claves privadas; **nunca** se agrega a la app la clave de una cuenta de servicio (esa es del servidor).

```bash
cp ~/Desktop/clinica-serena-local/firebase/google-services.json app/google-services.json
```

**Flujo en la app** (`core/push/PushRegistrationManager.kt`):
- El SDK no genera token al instalar (`firebase_messaging_auto_init_enabled=false`). Tras el login de un PACIENTE, si las notificaciones están permitidas, pide el token a FCM y lo registra con `PUT /pacientes/me/dispositivos-push`. Nunca para MEDICO.
- El permiso `POST_NOTIFICATIONS` (Android 13+) se pide en contexto: una invitación en Inicio («Ahora no» la oculta en esa sesión) y la sección **Recordatorios de citas** en Mi perfil, con «Abrir ajustes» si se rechazó. «Activados» aparece solo tras el 204.
- Renovación del token (`onNewToken`) o login nuevo: se vuelve a registrar (es idempotente). El token no se guarda en disco ni se escribe en logs.
- **Cerrar sesión:** `POST …/dispositivos-push/retiro` con el Bearer aún válido, luego `/auth/logout` (que también retira el dispositivo de esa sesión) y al final `deleteToken()` en FCM. Si no hay red, una marca persistente reintenta el borrado en el siguiente cierre o login de otra cuenta.
- **Vencimiento de la sesión (30 min):** no retira el dispositivo, igual que el backend; el paciente sigue recibiendo recordatorios.

**Backend local con FCM** (sin editar `.env`): `CLINICA_PUSH_PROVIDER=FIREBASE`, `CLINICA_PUSH_REMINDERS_ENABLED=false` y `GOOGLE_CLOUD_PROJECT=clinica-serena-push`, con credenciales de usuario de `gcloud auth application-default login` (no una clave de cuenta de servicio). Con el perfil `dev` el proveedor por defecto es `SIMULATED`, que rechaza tokens reales (400 `PUSH_TOKEN_NOT_SIMULATED`); con `NONE` responde 409 `PUSH_NOT_ENABLED`. La app muestra ambos casos como «No disponibles».

## 8. Solución de problemas

| Síntoma | Causa probable y solución |
|---|---|
| El backend no arranca: puerto 5432 en uso | Hay un PostgreSQL propio en la Mac. Usa `export DB_PORT=55432` (ya incluido en el comando de arranque); no detengas el Postgres de la Mac |
| `Cannot connect to the Docker daemon` | Docker Desktop está apagado: ábrelo, espera a que arranque y repite `docker compose up -d` |
| `docker compose up` intenta descargar la imagen y falla por red | La imagen ya está en la Mac: `docker compose up -d --pull never` |
| La app dice «No hay conexión con el servidor» | 1) El backend está apagado: `curl -s localhost:8080/api/v1/health` debe responder `UP`. 2) Teléfono físico: falta `scripts/dev-reverse.sh` (se pierde al reconectar) o la app no se compiló con la URL `localhost`. 3) Emulador sin red (ver la fila siguiente) |
| El emulador no tiene red (falla incluso internet) | Pasa a veces al reanudar desde una instantánea. **Arranque en frío**: en Android Studio, **Device Manager → ⋮ → Cold Boot Now**, o `emulator -avd <nombre> -no-snapshot-load` |
| El emulador queda detenido al arrancar tras un cierre inesperado | Está esperando el diálogo de informe de fallos. Respóndelo, o arranca con `-crash-report-mode never` |
| «Tu sesión venció» | El token dura 30 min y no hay refresh. Inicia sesión de nuevo. Si estabas en un formulario (atención, perfil clínico, adenda, observación, reserva o nombre), el borrador se conserva **solo en memoria** y se envía **una vez** tras el re-login |
| «Correo o contraseña incorrectos» con la contraseña correcta | La cuenta no está verificada, o hubo 5 intentos fallidos con ese correo (bloqueo de 60 s). Espera un minuto o verifica el correo |
| Médico: «Tu cuenta aún no está vinculada» | Un ADMIN debe vincular la cuenta a un profesional (`PUT /staff/accounts/{id}/practitioner`) |
| Médico: no puede registrar la atención | El detalle de la cita explica el motivo: falta la llegada (recepción), la cita aún no empieza, ya tiene atención o fue cancelada o completada |
| `SDK location not found` al compilar | Define `ANDROID_HOME` o `sdk.dir` en `local.properties` |
| El release falla con «clinicaSerena.releaseApiBaseUrl debe usar HTTPS» o con un mensaje de «Firma: …» | Revisa la URL y el archivo de firma (secciones 5 y 6) |
| No compila: `File google-services.json is missing` o `No matching client found for package name` | Falta `app/google-services.json` o no trae el cliente de esa variante (qaLocal usa `com.clinicaserena.app.qa`). Ver la sección 7 |
| Recordatorios: «No disponibles» | El backend tiene el proveedor push en `NONE` (409 `PUSH_NOT_ENABLED`) o en `SIMULATED` (400 `PUSH_TOKEN_NOT_SIMULATED`). Ver la sección 7 |
| Recordatorios: «No se pudieron activar» sin error del servidor | FCM no entregó token: emulador o teléfono sin Google Play Services, Play Services desactualizado o sin red |
| Instalar el release sobre el debug falla por firma distinta | Son llaves distintas; desinstala primero, o usa `qaLocal`, que se instala aparte |

## Seguridad y privacidad

- **Sesión:** el token Bearer se guarda cifrado con Android Keystore (AES-GCM) en DataStore. No sale en los respaldos (`allowBackup=false` y reglas de extracción).
- **Registro HTTP:** solo en debug, nivel BASIC, sin cabeceras ni cuerpos. Release y qaLocal no incluyen el interceptor.
- **Datos clínicos:** se muestran desde memoria y se borran al cerrar sesión. No hay caché persistente.
- **Autorización:** la app no autoriza nada con IDs de la UI; Spring valida cada operación. Todo recurso de otro médico responde 404.
- **Push:** el token FCM no se escribe en logs, no se guarda en disco y viaja solo en el cuerpo (nunca en la URL). La notificación trae solo fecha y hora. `google-services.json` no se versiona y la app nunca lleva claves de cuentas de servicio.
- **APK y secretos:** no se suben al repositorio APK, `.env`, keystores, `.properties` de firma ni informes locales.

## Pendientes conocidos

- Notificaciones push: la entrega real en staging y la activación en producción dependen del backend (credencial de servidor y `CLINICA_PUSH_PROVIDER=FIREBASE`). La app usa `getToken`/`deleteToken`, obsoletos desde `firebase-messaging` 25.1.0; migrar al registro por Installation ID cuando el backend lo valide.
- Subir `targetSdk` a 37 tras probar en Android 17.
- Definir la URL HTTPS de producción y la custodia de la llave definitiva.
- Dependencias de backend que faltan: ver el final de [`docs/matriz-endpoints.md`](docs/matriz-endpoints.md).
