import http from 'k6/http';
import { check } from 'k6';
import grpc from 'k6/net/grpc';

// -----------------------------------------------------------------------------
// Parameter / Basis-URLs
// -----------------------------------------------------------------------------

const PATTERN = __ENV.PATTERN || 'rest'; // 'rest' oder 'grpc'
const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const GRPC_TARGET = __ENV.GRPC_TARGET || 'host.docker.internal:9090';
const isGrpc = PATTERN === 'grpc';

// gRPC-Client und Proto
const grpcClient = new grpc.Client();
grpcClient.load(['/proto'], 'claims.proto');

// -----------------------------------------------------------------------------
// Szenarien
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
      // gRPC hat nur grpc_req_duration als Built-in-Metrik
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
    executeRestSubmitClaim();
  }
}

// -----------------------------------------------------------------------------
// REST-Szenario: POST /claims
// -----------------------------------------------------------------------------

function executeRestSubmitClaim() {
  const url = `${BASE_URL}/claims`;

  const payload = JSON.stringify({
    policyId: '11111111-1111-1111-1111-111111111111',
    customerId: '22222222-2222-2222-2222-222222222222',
    description: 'Test claim for load test',
    reportedAmount: 1000.0,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'REST status is 2xx': (r) => r.status >= 200 && r.status < 300,
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
    description: 'Test claim for load test',
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
