import http from 'k6/http';
import { check } from 'k6';
import grpc from 'k6/net/grpc';

// -----------------------------------------------------------------------------
// Parameter / Basis-URLs
// -----------------------------------------------------------------------------
//
// PATTERN steuert das globale Kommunikationsmuster im gesamten Szenario:
//
//   PATTERN=rest   → Claim/Policy/Customer mit REST-Inter-Service-Kommunikation
//   PATTERN=grpc   → Claim/Policy/Customer mit gRPC-Kommunikation
//   PATTERN=event  → Claim/Policy/Customer mit Event-driven-Kommunikation
//
// Aus Sicht von k6 unterscheiden wir nur zwischen
//   - gRPC (direkter gRPC-Client)
//   - HTTP (REST und EVENT teilen sich denselben HTTP-Endpunkt /claims)
// -----------------------------------------------------------------------------

const PATTERN = __ENV.PATTERN || 'rest'; // 'rest', 'grpc' oder 'event'

// Default-URLs für das Docker-Compose-Szenario.
// Können bei Bedarf über Environment-Variablen überschrieben werden.
const BASE_URL = __ENV.BASE_URL || 'http://claim-service:8080';
const GRPC_TARGET = __ENV.GRPC_TARGET || 'claim-service:9090';

const isGrpc = PATTERN === 'grpc';

// gRPC-Client und Proto
const grpcClient = new grpc.Client();
// In Docker wird /proto auf claim-service/claim-service/src/main/proto gemountet
grpcClient.load(['/proto'], 'claims.proto');

// -----------------------------------------------------------------------------
// Szenarien (Warmup + Breakpoint) – unverändert aus deiner bisherigen Version
// -----------------------------------------------------------------------------

const scenarios = {
  warmup: {
    executor: 'constant-arrival-rate',
    rate: 20,
    timeUnit: '1s',
    duration: '5m',
    preAllocatedVUs: 20,
    maxVUs: 50,
    startTime: '0s',
  },
  breakpoint: {
    executor: 'ramping-arrival-rate',
    startRate: 20,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: 200,
    startTime: '5m',
    stages: [
      { target: 20, duration: '5m' },
      { target: 40, duration: '5m' },
      { target: 60, duration: '5m' },
      { target: 80, duration: '5m' },
      { target: 100, duration: '5m' },
      { target: 120, duration: '5m' },
    ],
  },
};

const thresholds = isGrpc
  ? {
      // gRPC hat grpc_req_duration als Built-in-Metrik
      grpc_req_duration: ['p(95)<500'],
    }
  : {
      // REST und EVENT verwenden denselben HTTP-Endpunkt /claims,
      // unterscheiden sich aber in der internen Kommunikation der Services.
      http_req_duration: ['p(95)<500'],
      http_req_failed: ['rate<0.01'],
    };

export const options = {
  discardResponseBodies: true,
  thresholds,
  scenarios,
};

// -----------------------------------------------------------------------------
// k6 Entry-Point
// -----------------------------------------------------------------------------

export default function () {
  if (isGrpc) {
    executeGrpcSubmitClaim();
  } else {
    // PATTERN = 'rest' oder 'event' → HTTP-Aufruf /claims
    executeRestOrEventSubmitClaim();
  }
}

// -----------------------------------------------------------------------------
// HTTP-Szenario (REST + EVENT): POST /claims
// -----------------------------------------------------------------------------

function executeRestOrEventSubmitClaim() {
  const url = `${BASE_URL}/claims`;

  const payload = JSON.stringify({
    policyId: '11111111-1111-1111-1111-111111111111',
    customerId: '11111111-1111-1111-1111-111111111111',
    description: `Test claim for load test (${PATTERN})`,
    reportedAmount: 1000.0,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'HTTP status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });
}

// -----------------------------------------------------------------------------
// gRPC-Szenario: ClaimsService.SubmitClaim
// -----------------------------------------------------------------------------

function executeGrpcSubmitClaim() {
  grpcClient.connect(GRPC_TARGET, { plaintext: true });

  const request = {
    policyId: '11111111-1111-1111-1111-111111111111',
    customerId: '11111111-1111-1111-1111-111111111111',
    description: 'Test claim for load test (grpc)',
    reportedAmount: 1000.0,
  };

  const response = grpcClient.invoke(
    'claims.ClaimsService/SubmitClaim',
    request,
  );

  check(response, {
    'gRPC status is OK': (r) => r && r.status === grpc.StatusOK,
  });

  grpcClient.close();
}
