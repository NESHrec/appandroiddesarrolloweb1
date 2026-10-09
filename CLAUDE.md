# Clínica Serena — App Android (paciente y médico)

## Contexto
- App Android nativa que consume la API Spring Boot de Clínica Serena. No tiene BD clínica propia ni usa el BFF de Next.js.
- Backend: /Users/alejandroherrera/Desktop/Backproyectodesarolloodonto1 (rama `desarrollo`). Es SOLO LECTURA, salvo una tarea explícita de backend.
- Contrato: `docs/openapi.yaml` del backend. Si el OpenAPI o el README difieren del código, manda el código.
- Roles de la app: PACIENTE y MEDICO. ADMIN y RECEPCION quedan fuera de alcance.

## Stack y arquitectura
- Kotlin + Jetpack Compose, MVVM: UI → ViewModel → casos de uso/repositorio → cliente API.
- Corrutinas y Flow. DTO de API separados de los modelos de pantalla.
- Capas: `core/` (network, security, ui), `feature/*`, `data/`, `domain/`.
- No agregar librerías innecesarias. Antes de agregar una dependencia, confirmar su versión estable y su compatibilidad.

## Decisiones tomadas
- Login: `POST /api/v1/auth/login-unified`, sin selector de rol. Según `accountType`, usar `/auth/me` y `/auth/logout` (PACIENTE) o `/staff/auth/me` y `/staff/auth/logout` (PERSONAL). Si el rol es ADMIN o RECEPCION: mensaje claro y logout inmediato.
- Token Bearer opaco, TTL de 1800 s y sin refresh. Ante un 401: re-login. Si el usuario estaba en un formulario (por ejemplo, registrar atención), conservar el borrador SOLO en memoria (ViewModel) y reintentar después del re-login.
- El registro se hace desde la app. La verificación de correo y la recuperación de contraseña se completan en la web, mediante los enlaces del correo.
- Notificaciones push: Fase D. Requieren cambios en el backend, que se harán como tarea separada en su propia rama.
- Conteo de pacientes del médico: se calcula en el cliente sobre `/medico/citas` (`limit` máx. 100), con período explícito. Si llegan 100 resultados, advertir que el conteo puede estar incompleto.
- Odontograma: son observaciones append-only. Se muestran como historial por pieza y superficie, nunca como "estado actual".
- Detalle de cita del paciente: se construye con los datos de la lista, porque no hay endpoint de detalle.
- Registrar atención: habilitar o deshabilitar el botón según `canRecordAttention` y `attentionBlockers`, explicando el motivo.

## Reglas obligatorias
1. No inventar endpoints ni campos: verificarlos en el código del backend. Si falta algo, registrar el bloqueo; no simularlo.
2. Nunca escribir en logs, errores o analytics el Bearer, las contraseñas ni los tokens de verificación o recuperación.
3. Guardar el token con una solución mantenida respaldada por Android Keystore (EncryptedSharedPreferences está deprecado). Nunca en texto plano.
4. No guardar en caché persistente expedientes, diagnósticos, recetas ni odontogramas. Al hacer logout, limpiar el estado en memoria.
5. No autorizar con IDs que vengan de la UI. Spring valida cada operación.
6. Errores tipados según `ApiError {status, code, message, fieldErrors}`. Distinguir 401, 403, 404 y 409 por su `code`; no convertirlos en un error genérico.
7. Fechas: `OffsetDateTime` ISO 8601, mostradas en America/Guatemala. Para reservar, reenviar el `startAt` del bloque tal cual llegó. Codificar el `+` de los offsets en los query params.
8. Toda pantalla con datos maneja estos estados: carga, vacío, error recuperable, sesión vencida, permiso denegado y éxito. Textos en español.
9. Mostrar éxito solo después de una respuesta exitosa de Spring.
10. URL base por BuildConfig. Debug: `http://10.0.2.2:8080/api/v1`, con cleartext permitido SOLO en debug. Release: HTTPS.
11. Paleta: principal `#62727B`, fondo `#F6FAFA`, turquesa `#DDF3F1`, amarillo `#F8EDD2`, verde `#E5F1D8`, rosa `#F8E2E8`, texto `#17232B`, superficie `#FFFFFF`. No comunicar estados solo con color.
12. Nada está terminado solo porque compila. Probar el recorrido contra el backend real.

## Entorno local verificado (Fase A1)
- Mi Mac tiene un PostgreSQL 17 propio en el puerto 5432: NO tocarlo. El Postgres de Docker del backend usa el puerto 55432.
- Levantar el backend (sin editar .env):
  cd ~/Desktop/Backproyectodesarolloodonto1 && set -a && source .env && set +a && export DB_PORT=55432 JAVA_HOME=$(/usr/libexec/java_home -v 21) && docker compose up -d && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
- Comprobar: curl -s localhost:8080/api/v1/health → {"status":"UP"}. Mailpit: http://localhost:8025.
- Cuentas de prueba (paciente, ADMIN, médico, recepción): ~/Desktop/clinica-serena-local/cuentas-prueba.env. Nunca mostrar su contenido ni copiarlo al repo.
- Médico de prueba vinculado a la Dra. Elena Morales (Odontología general, practitionerId 20000000-0000-0000-0000-000000000001).
- Verificado en vivo: Spring devuelve las fechas en UTC con Z (ej. 2026-10-07T06:11:00Z) y acepta scheduledAt en Z.
- Verificado en vivo (A2): ruta inexistente con sesión → 403 FORBIDDEN si queda fuera de los prefijos permitidos al rol (paciente → /ruta-que-no-existe), pero 404 RESOURCE_NOT_FOUND si queda dentro de uno permitido (médico → /medico/ruta-que-no-existe); sin sesión → 401 UNAUTHENTICATED. Un bloque libre deja de aparecer en disponibilidad en cuanto inicia (para probar reservas se necesitan bloques futuros creados por el médico).
- Verificado en vivo (B1): tras 5 fallos de login con el mismo correo, incluso la contraseña correcta recibe 401 AUTHENTICATION_FAILED (idéntico a credenciales incorrectas) durante 60 s; después vuelve a entrar (200). Un paciente sin verificar recibe el mismo 401. La app muestra un único mensaje que cubre los tres casos.
- Verificado en vivo (C1, con dos médicos): todo recurso de otro médico → 404, nunca 403. Cita, expediente, atención, perfil clínico y adenda ajenos → 404 APPOINTMENT_NOT_FOUND; odontograma de un paciente sin citas con ese médico (GET) o sobre una cita ajena (POST) → 404 PATIENT_NOT_FOUND. Ninguna de esas peticiones deja efectos. Cada médico solo ve sus citas en /medico/citas. Un médico sin vincular recibe 403 PRACTITIONER_LINK_REQUIRED en /medico/**.
- Cuentas de prueba: además de paciente, ADMIN, médico y recepción, existen Paciente 2 (registrado desde la app en B1) y Médico 2 (vinculado a Dr. Mateo Rivera, Ortodoncia; tiene una cita de Paciente 2 el 10/10 a las 11:00).

## Git
- No hacer commit ni push sin que yo lo pida.
- No subir APK, secretos, `.env` ni informes locales.

## Acciones prohibidas sin pedirme permiso
- git commit, git push, git reset, git rebase o cualquier cambio de historial.
- Borrar volúmenes o contenedores de Docker (docker rm, docker volume rm, docker compose down -v) o detener contenedores de otros proyectos.
- Detener o modificar el PostgreSQL 17 de mi Mac (brew services, launchctl, kill).
- rm -rf o borrar archivos fuera del repo de la app y de ~/Desktop/clinica-serena-local/.
- Modificar archivos del repo del backend o mi ~/.zshrc.
- Instalar o desinstalar software del sistema (brew install, sdkmanager --uninstall).
- git checkout, git restore o git stash sobre archivos con cambios sin commit (pueden descartar trabajo); explícame primero qué se perdería.

## Informe al terminar cada tarea
Al finalizar cada tarea o fase, entrega en la conversación (como texto, sin crear archivos) un informe con:
1. Qué se pidió y qué se hizo.
2. Archivos creados, modificados o eliminados (ruta y propósito).
3. Comandos ejecutados y su resultado (éxito o error, salida relevante resumida).
4. Decisiones tomadas y su motivo, incluidas las versiones de dependencias.
5. Qué NO se hizo, bloqueos y pendientes.
6. Cómo verificarlo (qué ejecutar o mirar para comprobar que funciona).
7. Estado final de git (`git status`).
No marques nada como terminado si no se probó.
