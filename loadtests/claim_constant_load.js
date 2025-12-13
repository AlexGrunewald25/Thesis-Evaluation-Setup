import http from 'k6/http';
import { check, sleep } from 'k6';
import grpc from 'k6/net/grpc';
import exec from 'k6/execution';
import { Trend, Rate } from 'k6/metrics';

/**
 * Drop-in replacement for claim_constant_load.js
 *
 * Goals:
 * - Comparable metrics across REST / gRPC / event-driven runs
 * - Reuse gRPC connection per VU (no connect/close per iteration)
 * - Parameterizable test data (but defaults match your seeded UUIDs)
 * - Optional (research-only) E2E probe for event-driven via Prometheus counters
 */

// -----------------------------------------------------------------------------
// Env / defaults (compatible with run-loadtest.sh)
// -----------------------------------------------------------------------------

const PATTERN = __ENV.PATTERN || 'rest'; // rest | grpc | event-driven
const BASE_URL = __ENV.BASE_URL || 'http://claim-service:8080';
const GRPC_TARGET = __ENV.GRPC_TARGET || 'claim-service:9090';

const TEST_RUN = __ENV.TEST_RUN || 'local-constant';

// Arrival-rate parameters (passed by run-loadtest.sh)
const RATE = __ENV.RATE ? parseInt(__ENV.RATE, 10) : 80;
const DURATION = __ENV.DURATION || '20m';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 50;
const MAX_VUS = __ENV.MAX_VUS ? parseInt(__ENV.MAX_VUS, 10) : 200;

// Optional: capture response bodies to extract IDs (default stays memory-friendly)
const CAPTURE_IDS = (__ENV.CAPTURE_IDS || '0') === '1';

// Optional: event-driven E2E (prometheus-based, only meaningful at low rate / low concurrency)
const E2E_MODE = __ENV.E2E_MODE || 'none'; // none | prometheus
const PROM_URL = __ENV.PROM_URL || 'http://prometheus:9090';
const E2E_TIMEOUT_MS = __ENV.E2E_TIMEOUT_MS ? parseInt(__ENV.E2E_TIMEOUT_MS, 10) : 15000;
const E2E_POLL_INTERVAL_MS = __ENV.E2E_POLL_INTERVAL_MS ? parseInt(__ENV.E2E_POLL_INTERVAL_MS, 10) : 200;

// Test data pools (defaults reflect your seed data.sql)
const POLICY_IDS = parseCsvOrDefault(__ENV.POLICY_IDS, [
  '11111111-1111-1111-1111-111111111111',
  '22222222-2222-2222-2222-222222222222',
  '33333333-3333-3333-3333-333333333333',
]);
const CUSTOMER_IDS = parseCsvOrDefault(__ENV.CUSTOMER_IDS, [
  '11111111-1111-1111-1111-111111111111',
  '22222222-2222-2222-2222-222222222222',
]);

const isGrpc = PATTERN === 'grpc';
const isEventDriven = PATTERN === 'event-driven';

// -----------------------------------------------------------------------------
// Custom metrics (uniform across patterns)
// -----------------------------------------------------------------------------

const submit_ms = new Trend('submit_ms', true);
const submit_fail_rate = new Rate('submit_fail_rate');

const e2e_ms = new Trend('e2e_ms', true);
const e2e_fail_rate = new Rate('e2e_fail_rate');

// -----------------------------------------------------------------------------
// k6 options
// -----------------------------------------------------------------------------

export const options = {
  tags: {
    communication_pattern: PATTERN,
    service: 'claim-service',
    test_run: TEST_RUN,
    test_kind: 'constant',
  },

  // Keep default behavior unless you explicitly enable CAPTURE_IDS=1
  discardResponseBodies: !CAPTURE_IDS,

  scenarios: {
    constant_load: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: VUS,
      maxVUs: MAX_VUS,
      startTime: '0s',
      gracefulStop: '30s',
    },
  },

  thresholds: {
    // Uniform KPIs (works for REST/gRPC/event-driven)
    submit_ms: ['p(95)<500'],
    submit_fail_rate: ['rate<0.01'],

    // Only relevant if E2E_MODE is enabled
    ...(isEventDriven && E2E_MODE !== 'none'
      ? { e2e_fail_rate: ['rate<0.05'] }
      : {}),
  },
};

// -----------------------------------------------------------------------------
// gRPC client setup (per-VU runtime; no connect/close per iteration)
// -----------------------------------------------------------------------------

const grpcClient = new grpc.Client();
grpcClient.load(['/proto'], 'claims.proto');

let grpcConnected = false;

function ensureGrpcConnected() {
  if (grpcConnected) return;

  // NOTE: connect cannot be called during init; but this runs inside VU context.
  grpcClient.connect(GRPC_TARGET, { plaintext: true });
  grpcConnected = true;
}

// -----------------------------------------------------------------------------
// Main entry point
// -----------------------------------------------------------------------------

export default function () {
  if (isGrpc) {
    submitViaGrpc();
    return;
  }

  if (isEventDriven && E2E_MODE === 'prometheus') {
    // Research-only: run with LOW RATE (e.g. 1/s) if you want meaningful E2E.
    submitViaHttpAndProbeE2E();
    return;
  }

  submitViaHttp();
}

// -----------------------------------------------------------------------------
// Helpers: payload selection / IDs
// -----------------------------------------------------------------------------

function pickFrom(arr) {
  // deterministic distribution without relying on RNG (easier to reproduce)
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
// REST / event-driven over HTTP
// -----------------------------------------------------------------------------

function submitViaHttp() {
  const url = `${BASE_URL}/claims`;

  const payloadObj = buildCreatePayload();
  const payload = JSON.stringify(payloadObj);

  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { operation: 'submitClaim', protocol: 'http' },
    // Ensure we can parse JSON when CAPTURE_IDS=1
    ...(CAPTURE_IDS ? { responseType: 'text' } : {}),
  };

  const start = Date.now();
  const res = http.post(url, payload, params);
  submit_ms.add(Date.now() - start);

  const ok = check(res, {
    'submit: HTTP status is 201/2xx': (r) => r.status >= 200 && r.status < 300,
  });

  submit_fail_rate.add(!ok);
}

// -----------------------------------------------------------------------------
// gRPC
// -----------------------------------------------------------------------------

function submitViaGrpc() {
  ensureGrpcConnected();

  const payloadObj = buildCreatePayload();
  const request = {
    policyId: payloadObj.policyId,
    customerId: payloadObj.customerId,
    description: payloadObj.description,
    reportedAmount: payloadObj.reportedAmount,
  };

  const start = Date.now();
  const response = grpcClient.invoke(
    'claims.ClaimsService/SubmitClaim',
    request,
    {
      tags: { operation: 'submitClaim', protocol: 'grpc' },
      timeout: '30s',
    },
  );
  submit_ms.add(Date.now() - start);

  const ok = check(response, {
    'submit: gRPC status is OK': (r) => r && r.status === grpc.StatusOK,
  });

  // If the channel got closed/reset, try to reconnect once (best-effort).
  if (!ok) {
    submit_fail_rate.add(true);
    tryReconnectGrpc();
    return;
  }

  submit_fail_rate.add(false);
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
    // ignore: submit_fail_rate already recorded
  }
}

// -----------------------------------------------------------------------------
// Event-driven E2E probe via Prometheus (optional)
// -----------------------------------------------------------------------------

function submitViaHttpAndProbeE2E() {
  // We measure: submit latency + "time until claims-service has consumed BOTH
  // downstream results" (customer + policy), based on Prometheus counters.
  //
  // IMPORTANT: This is only meaningful when YOUR test is the only driver of these
  // counters. So: run with low RATE + low VUs.

  const basePolicy = promScalar('sum(claims_kafka_consumer_events_total{source="policy",outcome="success"})') ?? 0;
  const baseCustomer = promScalar('sum(claims_kafka_consumer_events_total{source="customer",outcome="success"})') ?? 0;

  submitViaHttp();

  const start = Date.now();
  const ok = waitUntil(() => {
    const nowPolicy = promScalar('sum(claims_kafka_consumer_events_total{source="policy",outcome="success"})') ?? 0;
    const nowCustomer = promScalar('sum(claims_kafka_consumer_events_total{source="customer",outcome="success"})') ?? 0;
    return nowPolicy >= basePolicy + 1 && nowCustomer >= baseCustomer + 1;
  }, E2E_TIMEOUT_MS, E2E_POLL_INTERVAL_MS);

  if (ok) {
    e2e_ms.add(Date.now() - start);
    e2e_fail_rate.add(false);
  } else {
    e2e_fail_rate.add(true);
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

function promScalar(query) {
  const url = `${PROM_URL}/api/v1/query?query=${encodeURIComponent(query)}`;
  const res = http.get(url, { tags: { operation: 'promQuery', protocol: 'http' } });
  if (res.status !== 200 || !res.body) return null;

  try {
    const data = JSON.parse(res.body);
    const result = data?.data?.result;
    if (!result || result.length === 0) return 0;

    // Prometheus returns: [ <timestamp>, "<value>" ]
    const v = result[0]?.value?.[1];
    const num = v !== undefined ? Number(v) : 0;
    return Number.isFinite(num) ? num : 0;
  } catch (_) {
    return null;
  }
}
