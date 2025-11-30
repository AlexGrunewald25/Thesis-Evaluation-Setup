import http from 'k6/http';
import { check } from 'k6';
import grpc from 'k6/net/grpc';

// -----------------------------------------------------------------------------
// Parameter / Basis-URLs
// -----------------------------------------------------------------------------
//
// PATTERN steuert global das Kommunikationsmuster (rest/grpc/event).
// Aus Sicht von k6 gilt:
//   - PATTERN === 'grpc'  → gRPC-Client
//   - PATTERN === 'rest'  → HTTP-Client
//   - PATTERN === 'event' → HTTP-Client, aber Services laufen im Event-Profil
// -----------------------------------------------------------------------------

const PATTERN = __ENV.PATTERN || 'rest';

// Default-URLs für das Docker-Compose-Szenario.
const BASE_URL = __ENV.BASE_URL || 'http://claim-service:8080';
const GRPC_TARGET = __ENV.GRPC_TARGET || 'claim-service:9090';

const isGrpc = PATTERN === 'grpc';

// Load-Parameter sind weiterhin über ENV variabel
const RATE = __ENV.RATE ? parseInt(__ENV.RATE, 10) : 80;
const DURATION = __ENV.DURATION || '20m';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 50;
const MAX_VUS = __ENV.MAX_VUS ? parseInt(__ENV.MAX_VUS, 10) : 200;

// gRPC-Client und Proto
const grpcClient = new grpc.Client();
grpcClient.load(['/proto'], 'claims.proto');

// -----------------------------------------------------------------------------
// Szenario-Konfiguration (konstante Arrival-Rate) – wie bisher
// -----------------------------------------------------------------------------

const scenarios = {
  constant_load: {
    executor: 'constant-arrival-rate',
    rate: RATE,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: VUS,
    maxVUs: MAX_VUS,
    startTime: '0s',
  },
};

const thresholds = isGrpc
  ? {
      grpc_req_duration: ['p(95)<500'],
    }
  : {
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
    customerId: '22222222-2222-2222-2222-222222222222',
    description: `Constant load test claim (${PATTERN})`,
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
    customerId: '22222222-2222-2222-2222-222222222222',
    description: 'Constant load test claim (grpc)',
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
