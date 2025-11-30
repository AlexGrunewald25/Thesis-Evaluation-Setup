#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Zentrale Steuerung des Evaluierungsszenarios
#
# - PATTERN ∈ {rest, grpc, event-driven}
#   → wird als SPRING_PROFILES_ACTIVE in docker-compose.yml verwendet
#   → wird in den k6-Containern als PATTERN-Env ausgelesen
#
# - Dieses Skript:
#   1. fragt Szenario und Testtyp ab (Breakpoint vs. Constant Load)
#   2. startet die Basis-Services (docker compose up -d)
#   3. führt den passenden k6-Lasttest aus
#   4. schreibt eine k6-Summary-JSON in ./results/
#   5. stoppt zum Schluss (oder bei Abbruch) nur die
#      profilabhängigen Services (claim/policy/customer + k6)
# ---------------------------------------------------------------------------

DC_BASE="docker compose"
DC_LOADTEST="docker compose --profile loadtest"

# Services, die beim Szenario-Wechsel ihr Profil ändern
APP_SERVICES="claim-service policy-service customer-service"
LOADTEST_SERVICES="k6-breakpoint k6-constant"

# Projektwurzel (Verzeichnis dieses Skripts) und Results-Ordner
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}"
HOST_RESULTS_DIR="${PROJECT_ROOT}/results"

# Git-Bash-/Windows-Artefakt aufräumen (falls vorhanden)
rm -rf "${PROJECT_ROOT}/results;C" 2>/dev/null || true

STACK_STARTED=0

cleanup() {
  # Wird bei EXIT / INT / TERM ausgeführt
  if [[ "${STACK_STARTED}" -eq 1 ]]; then
    echo
    echo "==================================================================="
    echo " Stoppe profilabhängige Services:"
    echo "   ${APP_SERVICES} ${LOADTEST_SERVICES}"
    echo "==================================================================="
    # Nur diese Services stoppen, Infrastruktur (DB, Kafka, Monitoring) bleibt laufen
    ${DC_BASE} stop ${APP_SERVICES} ${LOADTEST_SERVICES} 2>/dev/null || true

    echo
    echo "Entferne Container der profilabhängigen Services (ohne Volumes)..."
    ${DC_BASE} rm -f ${APP_SERVICES} ${LOADTEST_SERVICES} 2>/dev/null || true
  fi

  # Git-Bash-/Windows-Artefakt auch beim Abbruch entfernen
  rm -rf "${PROJECT_ROOT}/results;C" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Defaults für den Zugriff AUS DEN k6-CONTAINERN auf den Claim-Service
BASE_URL_DEFAULT="http://claim-service:8080"
GRPC_TARGET_DEFAULT="claim-service:9090"

# Override via Environment möglich, falls nötig
BASE_URL="${BASE_URL:-$BASE_URL_DEFAULT}"
GRPC_TARGET="${GRPC_TARGET:-$GRPC_TARGET_DEFAULT}"

echo "==================================================================="
echo " Evaluierungsszenario – Auswahl"
echo "==================================================================="
echo "1) REST         – Breakpoint-Lasttest      (PATTERN=rest)"
echo "2) gRPC         – Breakpoint-Lasttest      (PATTERN=grpc)"
echo "3) Event-driven – Breakpoint-Lasttest      (PATTERN=event-driven)"
echo "4) REST         – Constant Load            (PATTERN=rest)"
echo "5) gRPC         – Constant Load            (PATTERN=grpc)"
echo "6) Event-driven – Constant Load            (PATTERN=event-driven)"
echo "-------------------------------------------------------------------"
read -rp "Auswahl (1-6): " choice

TEST_KIND=""   # "breakpoint" oder "constant"
PATTERN=""     # "rest" | "grpc" | "event-driven"

case "$choice" in
  1)
    echo "→ Szenario: REST, Breakpoint-Lasttest"
    TEST_KIND="breakpoint"
    PATTERN="rest"
    ;;
  2)
    echo "→ Szenario: gRPC, Breakpoint-Lasttest"
    TEST_KIND="breakpoint"
    PATTERN="grpc"
    ;;
  3)
    echo "→ Szenario: Event-driven, Breakpoint-Lasttest"
    TEST_KIND="breakpoint"
    PATTERN="event-driven"
    ;;
  4)
    echo "→ Szenario: REST, Constant Load"
    TEST_KIND="constant"
    PATTERN="rest"
    ;;
  5)
    echo "→ Szenario: gRPC, Constant Load"
    TEST_KIND="constant"
    PATTERN="grpc"
    ;;
  6)
    echo "→ Szenario: Event-driven, Constant Load"
    TEST_KIND="constant"
    PATTERN="event-driven"
    ;;
  *)
    echo "Ungültige Auswahl."
    exit 1
    ;;
esac

# PATTERN wird:
# - als SPRING_PROFILES_ACTIVE in docker-compose.yml verwendet
#   (SPRING_PROFILES_ACTIVE: ${PATTERN:-rest})
# - in den k6-Containern als PATTERN-Env ausgelesen
export PATTERN
export BASE_URL
export GRPC_TARGET

echo
echo "==================================================================="
echo " Starte Basis-Services (Postgres, Kafka, Microservices, Monitoring)"
echo "==================================================================="
${DC_BASE} up -d
STACK_STARTED=1

echo
echo "Warte einige Sekunden, bis die Services bereit sind..."
sleep 20

# Ergebnisse-Verzeichnis vorbereiten
mkdir -p "${HOST_RESULTS_DIR}"
RUN_ID="$(date +%Y%m%d_%H%M%S)"

if [[ "${TEST_KIND}" == "breakpoint" ]]; then
  SUMMARY_NAME="summary_${PATTERN}_breakpoint_${RUN_ID}.json"
  SUMMARY_PATH_HOST="${HOST_RESULTS_DIR}/${SUMMARY_NAME}"
  SUMMARY_PATH_CONT="${SUMMARY_NAME}"   # relativ im Working Dir /results

  echo
  echo "==================================================================="
  echo " Starte k6-Breakpoint-Lasttest (PATTERN=${PATTERN})"
  echo " Summary wird geschrieben nach: ${SUMMARY_PATH_HOST}"
  echo "==================================================================="

  ${DC_LOADTEST} run --rm \
    -e PATTERN \
    -e BASE_URL \
    -e GRPC_TARGET \
    -v "${HOST_RESULTS_DIR}:/results" \
    k6-breakpoint \
    --summary-export="${SUMMARY_PATH_CONT}"

else
  # Constant-Load-Szenario – Default-Lastparameter (überschreibbar via Env)
  RATE="${RATE:-80}"
  DURATION="${DURATION:-20m}"
  VUS="${VUS:-50}"
  MAX_VUS="${MAX_VUS:-200}"

  SUMMARY_NAME="summary_${PATTERN}_constant_${RUN_ID}.json"
  SUMMARY_PATH_HOST="${HOST_RESULTS_DIR}/${SUMMARY_NAME}"
  SUMMARY_PATH_CONT="${SUMMARY_NAME}"   # relativ im Working Dir /results

  echo
  echo "==================================================================="
  echo " Starte k6-Constant-Load-Lasttest (PATTERN=${PATTERN})"
  echo " RATE=${RATE}, DURATION=${DURATION}, VUS=${VUS}, MAX_VUS=${MAX_VUS}"
  echo " Summary wird geschrieben nach: ${SUMMARY_PATH_HOST}"
  echo "==================================================================="

  ${DC_LOADTEST} run --rm \
    -e PATTERN \
    -e BASE_URL \
    -e GRPC_TARGET \
    -e RATE \
    -e DURATION \
    -e VUS \
    -e MAX_VUS \
    -v "${HOST_RESULTS_DIR}:/results" \
    k6-constant \
    --summary-export="${SUMMARY_PATH_CONT}"
fi

echo
echo "==================================================================="
echo " Lasttest abgeschlossen."
echo " Summary-Datei: ${SUMMARY_PATH_HOST}"
echo
echo " Profilabhängige Services werden nun beendet;"
echo " Infrastruktur (DB, Kafka, Monitoring) bleibt für weitere Analysen laufen."
echo "==================================================================="
