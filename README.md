# Thesis Evaluation Setup – Evaluation Prototype & Load Test Runner

This repository contains the evaluation prototype (microservices + monitoring + k6 load tests) used for the thesis evaluation.

The main entry point is the script `./run-loadtest.sh`. It lets you choose an evaluation scenario (REST / gRPC / event-driven) and a test type (Breakpoint / Constant Load / E2E Probe). The script then starts the required Docker Compose services, runs the k6 test, and writes results to `./results/`.

---

## Prerequisites

- Bash (Linux/macOS or **Git Bash/WSL** on Windows)
- Docker + Docker Compose v2 (`docker compose ...`)
- Optional (for analysis/monitoring):
  - Browser access to Grafana/Prometheus (see ports below)
- Optional (for resource export):
  - Python 3 (`python3` or `python`)

> Note (Windows/Git Bash): The script sets `MSYS_NO_PATHCONV=1` to prevent path conversion issues when calling Docker.

---

## Quickstart

1. Clone the repository and change into the directory:
   ```bash
   git clone <REPO_URL>
   cd <REPO_DIR>
   ```

2. Make the script executable (if needed):
   ```bash
   chmod +x run-loadtest.sh
   ```

3. Start the load test:
   ```bash
   ./run-loadtest.sh
   ```

4. Select one of the scenarios (1–9) in the menu. The test will then run automatically.

---

## Scenario Selection in the Script

When starting, `run-loadtest.sh` offers the following options:

- **Breakpoint**
  1) REST – Breakpoint  
  2) gRPC – Breakpoint  
  3) Event-driven – Breakpoint  

- **Constant Load**
  4) REST – Constant Load  
  5) gRPC – Constant Load  
  6) Event-driven – Constant Load  

- **E2E Probe**
  7) REST – E2E Probe  
  8) gRPC – E2E Probe  
  9) Event-driven – E2E Probe  

Internally, the script sets:
- `PATTERN` ∈ `{rest, grpc, event-driven}`
- `TEST_KIND` ∈ `{breakpoint, constant, e2e}`
- `TEST_RUN` is generated automatically (e.g., `rest_constant_20260127_123456`)

---

## What Happens During a Run?

High-level flow:

1. Select `PATTERN` and `TEST_KIND`
2. Start the **base services** via Docker Compose:
   - Kafka/Zookeeper
   - Postgres
   - Microservices (Claim/Policy/Customer)
   - Prometheus + Grafana (+ exporter)
3. Start the matching **k6 container** (Compose profile `loadtest`)
4. Write outputs to `./results/`
5. Optional: export resource metrics from Prometheus (via `export_resources_from_prom.py`)

> Cleanup: On exit, the script stops/removes the profile-dependent services (especially the k6 container).

---

## Monitoring (Ports)

From `docker-compose.yml` (among others):

- **Grafana:** `http://localhost:3000` (default: `admin` / `admin`)
- **Prometheus:** `http://localhost:9091` (host port 9091 → container port 9090)
- **Services:**
  - Claim service: `http://localhost:8080`
  - Policy service: `http://localhost:8081`
  - Customer service: `http://localhost:8083`

---

## Configuration via Environment Variables

You can override defaults using environment variables (before the call or inline):

### Base URLs / Targets
- `BASE_URL` (default: `http://claim-service:8080`) – from the k6 containers’ perspective
- `GRPC_TARGET` (default: `claim-service:9090`)
- `K6_PROMETHEUS_RW_SERVER_URL` (default: `http://prometheus:9090/api/v1/write`) – remote write receiver inside the Docker network
- `PROM_EXPORT_URL` (default: `http://localhost:9091`) – host URL used for the resource export
- `SERVICE_REGEX` (default: `claim-service|policy-service|customer-service|postgres|kafka|zookeeper`) – which services are included in the resource export

### Constant Load (script defaults)
- `RATE` (default: `80`)
- `DURATION` (default: `20m`)
- `VUS` (default: `50`)
- `MAX_VUS` (default: `200`)

### E2E Probe (script defaults)
- `RATE` (default: `1`)
- `DURATION` (default: `10m`)
- `VUS` (default: `1`)
- `MAX_VUS` (default: `1`)
- optional: `E2E_TIMEOUT_MS`, `E2E_POLL_INTERVAL_MS`

### Breakpoint
- `WARMUP_*` (e.g., `WARMUP_RATE`, `WARMUP_DURATION`, `WARMUP_VUS`, `WARMUP_MAX_VUS`)
- `BP_*` (e.g., `BP_START_RATE`, `BP_TIME_UNIT`, `BP_PREALLOC_VUS`, `BP_MAX_VUS`, `BP_STAGES`)
- `ABORT_ON_FAIL`, `DELAY_ABORT_EVAL`

Example (Constant Load, REST):
```bash
RATE=120 DURATION=15m VUS=60 MAX_VUS=250 ./run-loadtest.sh
```

---

## Results / Artifacts

All outputs are written under `./results/`:

- `summary_<TEST_RUN>.json` – k6 summary export
- `runinfo_<TEST_RUN>.json` – metadata (pattern/test type/parameters/targets)
- `resources_<TEST_RUN>.json` – resource metrics exported from Prometheus

---

## Project Structure (Short Overview)

- `run-loadtest.sh` – entry point (menu, compose start, k6 run, export, artifacts)
- `docker-compose.yml` – infrastructure + microservices + k6 profiles
- `loadtests/` – k6 scripts (e.g., `claim_breakpoint.js`, `claim_constant_load.js`, `claim_e2e_probe.js`)
- `monitoring/` – Prometheus configuration
- `grafana/` – dashboards + provisioning
- `postgres/init/` – DB initialization (schemas/users/etc.)
- `claim-service/`, `policy-service/`, `customer-service/` – microservices
- `export_resources_from_prom.py` – resource export from Prometheus

---

## Troubleshooting

### `permission denied` when running the script
```bash
chmod +x run-loadtest.sh
```

### Docker/Compose not available
- Check whether Docker is running:
  ```bash
  docker info
  docker compose version
  ```
- On Windows: verify Docker Desktop + WSL2 backend configuration.

### Ports already in use (e.g., 3000/9091/8080)
- Identify and free the ports, or adjust port mappings in Compose.

### Resource export fails
- Ensure Python is available (`python3` or `python`)
- Ensure Prometheus is reachable at `http://localhost:9091`
- If you are not using Windows/Git Bash: the `cygpath` parts are Windows-specific and can be adjusted/simplified on Linux/macOS.
