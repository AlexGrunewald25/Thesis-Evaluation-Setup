#!/usr/bin/env bash
set -euo pipefail

DC="docker compose --profile loadtest"
BASE_URL=${BASE_URL:-http://host.docker.internal:8080}
GRPC_TARGET=${GRPC_TARGET:-host.docker.internal:9090}

echo "Welches Szenario möchtest du fahren?"
echo "1) REST, sync, Breakpoint (Profil rest-sync)"
echo "2) REST, event, Breakpoint (Profil event-driven)"
echo "3) gRPC, sync, Breakpoint (Profil grpc-sync)"
echo "4) gRPC, event, Breakpoint (Profil event-driven)"
echo "5) REST, sync, Constant Load (Profil rest-sync)"
echo "6) gRPC, sync, Constant Load (Profil grpc-sync)"
read -rp "Auswahl (1-6): " choice

case "$choice" in
  1)
    echo "Starte REST sync Breakpoint..."
    PATTERN=rest
    $DC run --rm \
      -e PATTERN=$PATTERN \
      -e BASE_URL=$BASE_URL \
      k6-breakpoint
    ;;
  2)
    echo "Starte REST event Breakpoint..."
    PATTERN=rest
    $DC run --rm \
      -e PATTERN=$PATTERN \
      -e BASE_URL=$BASE_URL \
      k6-breakpoint
    ;;
  3)
    echo "Starte gRPC sync Breakpoint..."
    PATTERN=grpc
    $DC run --rm \
      -e PATTERN=$PATTERN \
      -e GRPC_TARGET=$GRPC_TARGET \
      k6-breakpoint
    ;;
  4)
    echo "Starte gRPC event Breakpoint..."
    PATTERN=grpc
    $DC run --rm \
      -e PATTERN=$PATTERN \
      -e GRPC_TARGET=$GRPC_TARGET \
      k6-breakpoint
    ;;
  5)
    echo "Starte REST sync Constant Load..."
    PATTERN=rest
    RATE=${RATE:-80}
    DURATION=${DURATION:-20m}
    $DC run --rm \
      -e PATTERN=$PATTERN \
      -e BASE_URL=$BASE_URL \
      -e RATE=$RATE \
      -e DURATION=$DURATION \
      k6-constant
    ;;
  6)
    echo "Starte gRPC sync Constant Load..."
    PATTERN=grpc
    RATE=${RATE:-60}
    DURATION=${DURATION:-20m}
    $DC run --rm \
      -e PATTERN=$PATTERN \
      -e GRPC_TARGET=$GRPC_TARGET \
      -e RATE=$RATE \
      -e DURATION=$DURATION \
      k6-constant
    ;;
  *)
    echo "Ungültige Auswahl."
    exit 1
    ;;
esac
