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

# Interne URLs aus Sicht der k6-Container (Compose-Netz)
export BASE_URL="${BASE_URL:-http://claim-service:8080}"
export GRPC_TARGET="${GRPC_TARGET:-claim-service:9090}"

# Remote Write: im Docker-Netz zählt Container-Port 9090
export K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://prometheus:9090/api/v1/write}"

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

if [[ "${TEST_KIND}" == "breakpoint" ]]; then
  echo
  echo "==================================================================="
  echo " Starte k6-Breakpoint-Lasttest"
  echo " TEST_RUN=${TEST_RUN}"
  echo "==================================================================="

  ${DC_LOADTEST} run --rm \
    -e PATTERN \
    -e BASE_URL \
    -e GRPC_TARGET \
    -e TEST_RUN \
    -e K6_PROMETHEUS_RW_SERVER_URL \
    k6-breakpoint \
    run /scripts/claim_breakpoint.js \
    --out experimental-prometheus-rw \
    --summary-export="${SUMMARY_PATH_IN_CONTAINER}"

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
fi
