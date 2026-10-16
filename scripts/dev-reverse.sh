#!/usr/bin/env bash
# Redirige el puerto del backend local (8080) desde cada dispositivo Android conectado hacia esta
# computadora con `adb reverse`. Así la app debug o qaLocal compilada con
#   -PclinicaSerena.debugApiBaseUrl=http://localhost:8080/api/v1/
# llega al backend tanto en un teléfono físico por USB como en un emulador.
#
# La redirección se pierde al reiniciar el emulador, reconectar el cable o reiniciar adb:
# vuelve a ejecutar este script en esos casos.
#
# Uso: scripts/dev-reverse.sh [puerto]   (por defecto 8080)
set -euo pipefail

PORT="${1:-8080}"
if ! [[ "$PORT" =~ ^[0-9]+$ ]] || (( PORT < 1 || PORT > 65535 )); then
  echo "Puerto inválido: $PORT" >&2
  exit 2
fi

if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  ADB="$ANDROID_HOME/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
elif [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
  ADB="$HOME/Library/Android/sdk/platform-tools/adb"
else
  echo "No se encontró adb. Define ANDROID_HOME o agrega platform-tools al PATH." >&2
  exit 1
fi

# Solo dispositivos en estado "device" (se omiten los offline o sin autorizar).
SERIALS=()
while read -r serial state _; do
  [[ "$state" == "device" ]] && SERIALS+=("$serial")
done < <("$ADB" devices | tail -n +2)

if (( ${#SERIALS[@]} == 0 )); then
  echo "No hay dispositivos conectados y autorizados (revisa 'adb devices' y acepta la depuración USB)." >&2
  exit 1
fi

status=0
for serial in "${SERIALS[@]}"; do
  if "$ADB" -s "$serial" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null; then
    echo "OK    $serial: tcp:$PORT del dispositivo → tcp:$PORT de esta computadora"
  else
    echo "ERROR $serial: no se pudo crear la redirección" >&2
    status=1
  fi
done
exit $status
