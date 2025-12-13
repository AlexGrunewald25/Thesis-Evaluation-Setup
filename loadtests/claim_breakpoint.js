import http from 'k6/http';
import { check } from 'k6';
import grpc from 'k6/net/grpc';
import exec from 'k6/execution';
import { Trend, Rate } from 'k6/metrics';

/**
 * Drop-in replacement for claim_breakpoint.js
 *
 * Goals:
 * - Comparable metrics across REST / gRPC / event-driven runs
 * - Reuse gRPC connection per VU (no connect/close per iteration)
 * - Parameterizable warmup + ramp stages via env vars
 */

// -----------------------------------------------------------------------------
// Env / defaults (compatible with run-loadtest.sh)
// -----------------------------------------------------------------------------

const PATTERN = __ENV.PATTERN || 'rest'; // rest | grpc | event-driven
const BASE_URL = __ENV.BASE_URL || 'http://claim-service:8080';
const GRPC_TARGET = __ENV.GRPC_TARGET || 'claim-service:9090';
const TEST_RUN = __ENV.TEST_RUN || 'local-breakpoint';

const CAPTURE_IDS = (__ENV.CAPTURE_IDS || '0') === '1';

// Warmup defaults (match your previous file)
const WARMUP_RATE = __ENV.WARMUP_RATE ? parseInt(__ENV.WARMUP_RATE, 10) : 20;
const WARMUP_DURATION = __ENV.WARMUP_DURATION || '5m';
const WARMUP_VUS = __ENV.WARMUP_VUS ? parseInt(__ENV.WARMUP_VUS, 10) : 20;
const WARMUP_MAX_VUS = __ENV.WARMUP_MAX_VUS ? parseInt(__ENV.WARMUP_MAX_VUS, 10) : 50;

// Breakpoint defaults (match your previous file)
const BP_START_RATE = __ENV.BP_START_RATE ? parseInt(__ENV.BP_START_RATE, 10) : 20;
const BP_TIME_UNIT = __ENV.BP_TIME_UNIT || '1s';
const BP_PREALLOC_VUS = __ENV.BP_PREALLOC_VUS ? parseInt(__ENV.BP_PREALLOC_VUS, 10) : 50;
const BP_MAX_VUS = __ENV.BP_MAX_VUS ? parseInt(__ENV.BP_MAX_VUS, 10) : 200;
const BP_STAGES = parseStages(__ENV.BP_STAGES) || [
  { target: 20, duration: '5m' },
  { target: 40, duration: '5m' },
  { target: 60, duration: '5m' },
  { target: 80, duration: '5m' },
  { target: 100, duration: '5m' },
  { target: 120, duration: '5m' },
];

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

// -----------------------------------------------------------------------------
// Custom metrics (uniform across patterns)
// -----------------------------------------------------------------------------

const submit_ms = new Trend('submit_ms', true);
const submit_fail_rate = new Rate('submit_fail_rate');

// -----------------------------------------------------------------------------
// k6 options
// -----------------------------------------------------------------------------

export const options = {
  tags: {
    communication_pattern: PATTERN,
    service: 'claim-service',
    test_run: TEST_RUN,
    test_kind: 'breakpoint',
  },

  discardResponseBodies: !CAPTURE_IDS,

  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: WARMUP_RATE,
      timeUnit: '1s',
      duration: WARMUP_DURATION,
      preAllocatedVUs: WARMUP_VUS,
      maxVUs: WARMUP_MAX_VUS,
      startTime: '0s',
      gracefulStop: '30s',
    },
    breakpoint: {
      executor: 'ramping-arrival-rate',
      startRate: BP_START_RATE,
      timeUnit: BP_TIME_UNIT,
      preAllocatedVUs: BP_PREALLOC_VUS,
      maxVUs: BP_MAX_VUS,
      startTime: WARMUP_DURATION,
      stages: BP_STAGES,
      gracefulStop: '30s',
    },
  },

  thresholds: {
    submit_ms: ['p(95)<500'],
    submit_fail_rate: ['rate<0.01'],
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
  grpcClient.connect(GRPC_TARGET, { plaintext: true });
  grpcConnected = true;
}

// -----------------------------------------------------------------------------
// Main entry point
// -----------------------------------------------------------------------------

export default function () {
  if (isGrpc) {
    submitViaGrpc();
  } else {
    submitViaHttp();
  }
}

// -----------------------------------------------------------------------------
// Helpers: payload selection / IDs
// -----------------------------------------------------------------------------

function pickFrom(arr) {
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

// Example: BP_STAGES="20:5m,40:5m,60:5m,80:5m,100:5m,120:5m"
function parseStages(value) {
  if (!value || value.trim() === '') return null;

  const stages = [];
  for (const part of value.split(',')) {
    const p = part.trim();
    if (!p) continue;
    const [targetStr, dur] = p.split(':').map((x) => x.trim());
    const target = parseInt(targetStr, 10);
    if (!Number.isFinite(target) || !dur) continue;
    stages.push({ target, duration: dur });
  }
  return stages.length > 0 ? stages : null;
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
    // ignore
  }
}
