#!/usr/bin/env bash
set -euo pipefail

DC_BASE="docker compose"
DC_LOADTEST="docker compose --profile loadtest"

APP_SERVICES="claim-service policy-service customer-service"
LOADTEST_SERVICES="k6-breakpoint k6-constant"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}"
HOST_RESULTS_DIR="${PROJECT_ROOT}/results"
mkdir -p "${HOST_RESULTS_DIR}"

# Git-Bash/MSYS: verhindert automatische Pfad-Konvertierung bei Docker-Aufrufen
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

cleanup() {
  echo
  echo "==================================================================="
  echo " Stoppe profilabhängige Services:"
  echo "   ${APP_SERVICES} ${LOADTEST_SERVICES}"
  echo "==================================================================="
  ${DC_BASE} stop ${APP_SERVICES} ${LOADTEST_SERVICES} 2>/dev/null || true
  ${DC_BASE} rm -f ${APP_SERVICES} ${LOADTEST_SERVICES} 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# -------------------------------------------------------------------
# URLs / defaults
# -------------------------------------------------------------------

# Interne URLs aus Sicht der k6-Container (Compose-Netz)
export BASE_URL="${BASE_URL:-http://claim-service:8080}"
export GRPC_TARGET="${GRPC_TARGET:-claim-service:9090}"

# Remote Write: im Docker-Netz zählt Container-Port 9090
export K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://prometheus:9090/api/v1/write}"

# Ressourcenexport: Prometheus Host-Port (bei dir 9091)
PROM_EXPORT_URL="${PROM_EXPORT_URL:-http://localhost:9091}"

# Welche Services sollen in resource-export einfließen?
SERVICE_REGEX="${SERVICE_REGEX:-claim-service|policy-service|customer-service|postgres|kafka|zookeeper}"

# Python executable (Windows kann "python" sein)
PYTHON_BIN="${PYTHON_BIN:-python3}"
if ! command -v "${PYTHON_BIN}" >/dev/null 2>&1; then
  PYTHON_BIN="python"
fi

echo "==================================================================="
echo " Evaluierungsszenario – Auswahl"
echo "==================================================================="
echo "1) REST         – Breakpoint"
echo "2) gRPC         – Breakpoint"
echo "3) Event-driven – Breakpoint"
echo "4) REST         – Constant Load"
echo "5) gRPC         – Constant Load"
echo "6) Event-driven – Constant Load"
echo "7) REST         – E2E Probe"
echo "8) gRPC         – E2E Probe"
echo "9) Event-driven – E2E Probe"
echo "-------------------------------------------------------------------"
read -rp "Auswahl (1-9): " choice

case "${choice}" in
  1) TEST_KIND="breakpoint"; PATTERN="rest" ;;
  2) TEST_KIND="breakpoint"; PATTERN="grpc" ;;
  3) TEST_KIND="breakpoint"; PATTERN="event-driven" ;;
  4) TEST_KIND="constant";   PATTERN="rest" ;;
  5) TEST_KIND="constant";   PATTERN="grpc" ;;
  6) TEST_KIND="constant";   PATTERN="event-driven" ;;
  7) TEST_KIND="e2e";        PATTERN="rest" ;;
  8) TEST_KIND="e2e";        PATTERN="grpc" ;;
  9) TEST_KIND="e2e";        PATTERN="event-driven" ;;
  *) echo "Ungültige Auswahl"; exit 1 ;;
esac

export PATTERN

echo
echo "==================================================================="
echo " Starte Basis-Services"
echo "==================================================================="
${DC_BASE} up -d --remove-orphans

sleep 20

RUN_ID="$(date +%Y%m%d_%H%M%S)"
export TEST_RUN="${PATTERN}_${TEST_KIND}_${RUN_ID}"

SUMMARY_NAME="summary_${TEST_RUN}.json"
SUMMARY_PATH_IN_CONTAINER="/results/${SUMMARY_NAME}"

RUNINFO_PATH="${HOST_RESULTS_DIR}/runinfo_${TEST_RUN}.json"
RESOURCES_PATH="${HOST_RESULTS_DIR}/resources_${TEST_RUN}.json"

# Helper: write run metadata (for later reproducibility / thesis traceability)
write_runinfo() {
  cat > "${RUNINFO_PATH}" <<EOF
{
  "test_run": "${TEST_RUN}",
  "pattern": "${PATTERN}",
  "test_kind": "${TEST_KIND}",
  "base_url": "${BASE_URL}",
  "grpc_target": "${GRPC_TARGET}",
  "prom_export_url": "${PROM_EXPORT_URL}",
  "service_regex": "${SERVICE_REGEX}",
  "params": {
    "RATE": "${RATE:-}",
    "DURATION": "${DURATION:-}",
    "VUS": "${VUS:-}",
    "MAX_VUS": "${MAX_VUS:-}",
    "WARMUP_RATE": "${WARMUP_RATE:-}",
    "WARMUP_DURATION": "${WARMUP_DURATION:-}",
    "WARMUP_VUS": "${WARMUP_VUS:-}",
    "WARMUP_MAX_VUS": "${WARMUP_MAX_VUS:-}",
    "BP_START_RATE": "${BP_START_RATE:-}",
    "BP_TIME_UNIT": "${BP_TIME_UNIT:-}",
    "BP_PREALLOC_VUS": "${BP_PREALLOC_VUS:-}",
    "BP_MAX_VUS": "${BP_MAX_VUS:-}",
    "BP_STAGES": "${BP_STAGES:-}",
    "ABORT_ON_FAIL": "${ABORT_ON_FAIL:-}",
    "DELAY_ABORT_EVAL": "${DELAY_ABORT_EVAL:-}"
  }
}
EOF
}

export_resources() {
  local start_epoch="$1"
  local end_epoch="$2"
  local step="$3"

  # small buffer for scrape alignment
  sleep 5

  if [[ ! -f "${PROJECT_ROOT}/export_resources_from_prom.py" ]]; then
    echo "WARN: export_resources_from_prom.py fehlt im Projektroot – Ressourcenexport übersprungen."
    return 0
  fi

  echo
  echo "==================================================================="
  echo " Exportiere Ressourcenkennwerte aus Prometheus"
  echo " URL=${PROM_EXPORT_URL}"
  echo " Zeitraum: ${start_epoch} .. ${end_epoch} (epoch)"
  echo "==================================================================="

  "${PYTHON_BIN}" "${PROJECT_ROOT}/export_resources_from_prom.py" \
    --prom-url "${PROM_EXPORT_URL}" \
    --start "${start_epoch}" \
    --end "${end_epoch}" \
    --step "${step}" \
    --test-run "${TEST_RUN}" \
    --service-regex "${SERVICE_REGEX}" \
    --out "${RESOURCES_PATH}"

  echo "OK: ${RESOURCES_PATH}"
}

# -------------------------------------------------------------------
# Run
# -------------------------------------------------------------------

RUN_START_EPOCH="$(date +%s)"

if [[ "${TEST_KIND}" == "breakpoint" ]]; then
  # Optional: falls du schnell eine stärkere Rampe willst, ohne env zu setzen:
  # export BP_STAGES='100:2m,200:2m,400:2m,800:2m,1200:2m,1600:2m,2000:2m'
  # export BP_MAX_VUS=2000
  # export BP_PREALLOC_VUS=200
  # export ABORT_ON_FAIL=1

  echo
  echo "==================================================================="
  echo " Starte k6-Breakpoint-Lasttest"
  echo " TEST_RUN=${TEST_RUN}"
  echo "==================================================================="

  write_runinfo

  ${DC_LOADTEST} run --rm \
    -e PATTERN \
    -e BASE_URL \
    -e GRPC_TARGET \
    -e TEST_RUN \
    -e K6_PROMETHEUS_RW_SERVER_URL \
    -e CAPTURE_IDS \
    -e POLICY_IDS \
    -e CUSTOMER_IDS \
    -e WARMUP_RATE \
    -e WARMUP_DURATION \
    -e WARMUP_VUS \
    -e WARMUP_MAX_VUS \
    -e BP_START_RATE \
    -e BP_TIME_UNIT \
    -e BP_PREALLOC_VUS \
    -e BP_MAX_VUS \
    -e BP_STAGES \
    -e ABORT_ON_FAIL \
    -e DELAY_ABORT_EVAL \
    k6-breakpoint \
    run /scripts/claim_breakpoint.js \
    --out experimental-prometheus-rw \
    --summary-export="${SUMMARY_PATH_IN_CONTAINER}"

  RUN_END_EPOCH="$(date +%s)"
  export_resources "${RUN_START_EPOCH}" "${RUN_END_EPOCH}" "10s"

else
  # Constant vs E2E: unterschiedliche Defaults + anderes Script
  if [[ "${TEST_KIND}" == "e2e" ]]; then
    RATE="${RATE:-1}"
    DURATION="${DURATION:-10m}"
    VUS="${VUS:-1}"
    MAX_VUS="${MAX_VUS:-1}"
    K6_SCRIPT="claim_e2e_probe.js"
    TEST_LABEL="E2E-Probe"
  else
    RATE="${RATE:-80}"
    DURATION="${DURATION:-20m}"
    VUS="${VUS:-50}"
    MAX_VUS="${MAX_VUS:-200}"
    K6_SCRIPT="claim_constant_load.js"
    TEST_LABEL="Constant-Load-Lasttest"
  fi

  export RATE DURATION VUS MAX_VUS

  echo
  echo "==================================================================="
  echo " Starte k6-${TEST_LABEL}"
  echo " TEST_RUN=${TEST_RUN}"
  echo " PATTERN=${PATTERN}"
  echo " RATE=${RATE}, DURATION=${DURATION}, VUS=${VUS}, MAX_VUS=${MAX_VUS}"
  echo " SCRIPT=${K6_SCRIPT}"
  echo "==================================================================="

  write_runinfo

  ${DC_LOADTEST} run --rm \
    -e PATTERN \
    -e BASE_URL \
    -e GRPC_TARGET \
    -e TEST_RUN \
    -e RATE \
    -e DURATION \
    -e VUS \
    -e MAX_VUS \
    -e PROM_URL \
    -e E2E_TIMEOUT_MS \
    -e E2E_POLL_INTERVAL_MS \
    -e CAPTURE_IDS \
    -e POLICY_IDS \
    -e CUSTOMER_IDS \
    -e K6_PROMETHEUS_RW_SERVER_URL \
    k6-constant \
    run "/scripts/${K6_SCRIPT}" \
    --out experimental-prometheus-rw \
    --summary-export="${SUMMARY_PATH_IN_CONTAINER}"

  RUN_END_EPOCH="$(date +%s)"

  # constant/e2e sind kürzer => feinere step möglich
  if [[ "${TEST_KIND}" == "e2e" ]]; then
    export_resources "${RUN_START_EPOCH}" "${RUN_END_EPOCH}" "5s"
  else
    export_resources "${RUN_START_EPOCH}" "${RUN_END_EPOCH}" "10s"
  fi
fi
