import http from 'k6/http';
import { check, sleep } from 'k6';
import grpc from 'k6/net/grpc';
import exec from 'k6/execution';
import { Trend, Rate } from 'k6/metrics';

/**
 * E2E probe (cross-pattern comparable)
 *
 * Purpose:
 * - Provide a truly comparable "end-to-end" latency KPI across:
 *   REST vs gRPC vs Kafka/event-driven, under the SAME prerequisites.
 *
 * Design:
 * - Closed model (constant-vus) to avoid overlapping requests that would
 *   break correlation for the event-driven E2E measurement (based on counters).
 * - For REST/gRPC: E2E == synchronous request latency.
 * - For event-driven: E2E == time from submit start until claim-service has
 *   consumed BOTH downstream result events (policy + customer), measured via
 *   Prometheus counters.
 *
 * How to run (via your run-loadtest.sh after you add the "e2e" menu entries):
 * - Use low rates / 1 VU for clean E2E:
 *   RATE=1 DURATION=10m VUS=1 MAX_VUS=1 ./run-loadtest.sh
 */

// -----------------------------------------------------------------------------
// Env / defaults
// -----------------------------------------------------------------------------

const PATTERN = __ENV.PATTERN || 'rest'; // rest | grpc | event-driven
const BASE_URL = __ENV.BASE_URL || 'http://claim-service:8080';
const GRPC_TARGET = __ENV.GRPC_TARGET || 'claim-service:9090';
const TEST_RUN = __ENV.TEST_RUN || 'local-e2e';

// Use RATE as target pacing (best-effort) for the closed model
const RATE = __ENV.RATE ? parseFloat(__ENV.RATE) : 1; // submits per second (best-effort)
const DURATION = __ENV.DURATION || '10m';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 1;

// Prometheus is part of your docker-compose; service name is "prometheus"
const PROM_URL = __ENV.PROM_URL || 'http://prometheus:9090';
const E2E_TIMEOUT_MS = __ENV.E2E_TIMEOUT_MS ? parseInt(__ENV.E2E_TIMEOUT_MS, 10) : 120000;
const E2E_POLL_INTERVAL_MS = __ENV.E2E_POLL_INTERVAL_MS ? parseInt(__ENV.E2E_POLL_INTERVAL_MS, 10) : 1000;

// Test data pools (defaults reflect your seed data.sql)
const POLICY_IDS = parseCsvOrDefault(__ENV.POLICY_IDS, [
  '22222222-2222-2222-2222-222222222222',
]);
const CUSTOMER_IDS = parseCsvOrDefault(__ENV.CUSTOMER_IDS, [
  '11111111-1111-1111-1111-111111111111',
]);

const isGrpc = PATTERN === 'grpc';
const isEventDriven = PATTERN === 'event-driven';

// -----------------------------------------------------------------------------
// Metrics
// -----------------------------------------------------------------------------

const submit_ms = new Trend('submit_ms', true);
const submit_fail_rate = new Rate('submit_fail_rate');

const e2e_ms = new Trend('e2e_ms', true);
const e2e_fail_rate = new Rate('e2e_fail_rate');

// -----------------------------------------------------------------------------
// k6 options (closed model => no overlap unless you set VUS>1)
// -----------------------------------------------------------------------------

export const options = {
  tags: {
    communication_pattern: PATTERN,
    service: 'claim-service',
    test_run: TEST_RUN,
    test_kind: 'e2e',
  },

  discardResponseBodies: true,

  scenarios: {
    e2e_probe: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '30s',
    },
  },

  thresholds: {
    // Make these strict/lenient as you like; they are here mainly to standardize outputs
    submit_fail_rate: ['rate<0.01'],
    e2e_fail_rate: ['rate<0.01'],
  },
};

// -----------------------------------------------------------------------------
// gRPC client setup (reuse per VU)
// -----------------------------------------------------------------------------

const grpcClient = new grpc.Client();
grpcClient.load(['/proto'], 'claims.proto');
let grpcConnected = false;

function ensureGrpcConnected() {
  if (grpcConnected) return;
  grpcClient.connect(GRPC_TARGET, { plaintext: true });
  grpcConnected = true;
}

// -----------------------------------------------------------------------------
// Main
// -----------------------------------------------------------------------------

export default function () {
  const iterationStart = Date.now();

  if (isGrpc) {
    probeGrpc();
  } else {
    probeHttp();
  }

  // Best-effort pacing using RATE (submits per second)
  pace(iterationStart);
}

// -----------------------------------------------------------------------------
// Probe implementations
// -----------------------------------------------------------------------------

function probeHttp() {
  const url = `${BASE_URL}/claims`;
  const payloadObj = buildCreatePayload();
  const payload = JSON.stringify(payloadObj);

  // For event-driven E2E, we must snapshot counters BEFORE submit
  let basePolicy = isEventDriven ? promScalar(sumConsumerQuery('policy')) : null;
  let baseCustomer = isEventDriven ? promScalar(sumConsumerQuery('customer')) : null;
  

  const start = Date.now();
  const res = http.post(url, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { operation: 'submitClaim', protocol: 'http' },
  });
  const submitDur = Date.now() - start;
  submit_ms.add(submitDur);

  const ok = check(res, {
    'submit: HTTP status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });
  submit_fail_rate.add(!ok);

  if (!ok) {
    e2e_fail_rate.add(true);
    return;
  }

  if (!isEventDriven) {
    // REST is synchronous => E2E == submit
    e2e_ms.add(submitDur);
    e2e_fail_rate.add(false);
    return;
  }

  // Event-driven E2E: wait until BOTH result-consumption counters advanced by >= 1
  const e2eOk = waitUntil(() => {
    const nowPolicy = promScalar(sumConsumerQuery('policy'));
    const nowCustomer = promScalar(sumConsumerQuery('customer'));

      // Counter-Reset/Restart abfangen:
  if (nowPolicy < basePolicy) basePolicy = nowPolicy;
  if (nowCustomer < baseCustomer) baseCustomer = nowCustomer;
  
    return nowPolicy >= (basePolicy + 1) && nowCustomer >= (baseCustomer + 1);
  }, E2E_TIMEOUT_MS, E2E_POLL_INTERVAL_MS);

  if (e2eOk) {
    e2e_ms.add(Date.now() - start);
    e2e_fail_rate.add(false);
  } else {
    e2e_fail_rate.add(true);
  }
}

function probeGrpc() {
  ensureGrpcConnected();

  const payloadObj = buildCreatePayload();
  const request = {
    policyId: payloadObj.policyId,
    customerId: payloadObj.customerId,
    description: payloadObj.description,
    reportedAmount: payloadObj.reportedAmount,
  };

  const start = Date.now();
  const response = grpcClient.invoke('claims.ClaimsService/SubmitClaim', request, {
    tags: { operation: 'submitClaim', protocol: 'grpc' },
    timeout: '30s',
  });
  const submitDur = Date.now() - start;
  submit_ms.add(submitDur);

  const ok = check(response, {
    'submit: gRPC status is OK': (r) => r && r.status === grpc.StatusOK,
  });
  submit_fail_rate.add(!ok);

  if (!ok) {
    e2e_fail_rate.add(true);
    // best-effort reconnect once
    tryReconnectGrpc();
    return;
  }

  // gRPC run is synchronous => E2E == submit
  e2e_ms.add(submitDur);
  e2e_fail_rate.add(false);
}

function tryReconnectGrpc() {
  try {
    grpcClient.close();
  } catch (_) {
    // ignore
  }
  grpcConnected = false;
  try {
    ensureGrpcConnected();
  } catch (_) {
    // ignore
  }
}

// -----------------------------------------------------------------------------
// Prometheus helpers (Micrometer -> Prometheus naming: dots become underscores)
// -----------------------------------------------------------------------------

function sumConsumerQuery(source) {
  // Metric is Counter.builder("claims.kafka.consumer.events") in claim-service consumers.
  // In Prometheus it becomes: claims_kafka_consumer_events_total
  return `sum(claims_kafka_consumer_events_total{source="${source}",outcome="success"})`;
}

function promScalar(query) {
  const url = `${PROM_URL}/api/v1/query?query=${encodeURIComponent(query)}`;
  const res = http.get(url, {
     tags: { operation: 'promQuery', protocol: 'http' },
     responseType: 'text', // wichtig, sonst ist res.body leer wegen discardResponseBodies
    });

  if (res.status !== 200 || !res.body) return 0;

  try {
    const data = JSON.parse(res.body);
    const result = data?.data?.result;
    if (!result || result.length === 0) return 0;
    const v = result[0]?.value?.[1];
    const num = v !== undefined ? Number(v) : 0;
    return Number.isFinite(num) ? num : 0;
  } catch (_) {
    return 0;
  }
}

function waitUntil(predicateFn, timeoutMs, pollIntervalMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (predicateFn()) return true;
    sleep(pollIntervalMs / 1000);
  }
  return false;
}

// -----------------------------------------------------------------------------
// Payload helpers
// -----------------------------------------------------------------------------

function pickFrom(arr) {
  // deterministic distribution without relying on RNG (reproducible)
  const vu = exec.vu.idInTest || 1;
  const it = exec.scenario.iterationInTest || 0;
  return arr[(vu + it) % arr.length];
}

function buildCreatePayload() {
  const policyId = pickFrom(POLICY_IDS);
  const customerId = pickFrom(CUSTOMER_IDS);
  const suffix = `run=${TEST_RUN}|vu=${exec.vu.idInTest}|it=${exec.scenario.iterationInTest}`;
  return {
    policyId,
    customerId,
    description: `SubmitClaim (${PATTERN}) ${suffix}`,
    reportedAmount: 1000.0,
  };
}

function parseCsvOrDefault(value, fallbackArr) {
  if (!value || value.trim() === '') return fallbackArr;
  return value
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

// -----------------------------------------------------------------------------
// Pacing
// -----------------------------------------------------------------------------

function pace(iterationStartMs) {
  if (!RATE || RATE <= 0) return;

  const targetPeriodMs = 1000 / RATE;
  const elapsedMs = Date.now() - iterationStartMs;
  const remainingMs = targetPeriodMs - elapsedMs;

  if (remainingMs > 0) {
    sleep(remainingMs / 1000);
  }
}
