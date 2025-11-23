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

const RATE = __ENV.RATE ? parseInt(__ENV.RATE, 10) : 80;
const DURATION = __ENV.DURATION || '20m';
const VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 50;
const MAX_VUS = __ENV.MAX_VUS ? parseInt(__ENV.MAX_VUS, 10) : 200;

// gRPC-Client und Proto
const grpcClient = new grpc.Client();
grpcClient.load(['/proto'], 'claims.proto');

// -----------------------------------------------------------------------------
// Szenario-Konfiguration
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
    description: 'Constant load test claim',
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
    description: 'Constant load test claim',
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
