package com.example.policies.infrastructure.grpc;

import com.example.policies.application.PolicyService;
import com.example.policies.domain.Policy;
import com.example.policies.grpc.GetPolicyRequest;
import com.example.policies.grpc.GetPolicyResponse;
import com.example.policies.grpc.PolicyServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

/**
 * gRPC-Endpoint für den Policy-Service.
 * Vorbild: ClaimsGrpcService im Claim-Service.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
@Profile("grpc")
public class PolicyGrpcService extends PolicyServiceGrpc.PolicyServiceImplBase {

    private final com.example.policies.application.PolicyService policyService;
    private final MeterRegistry meterRegistry;

    private Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    private void stopSample(Timer.Sample sample, String method, String outcome) {
        sample.stop(
                Timer.builder("policies.grpc.latency")
                        .description("gRPC latency per policies RPC")
                        .tag("method", method)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementCounter(String method, String outcome) {
        Counter.builder("policies.grpc.requests")
                .description("gRPC request count per policies RPC")
                .tag("method", method)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void getPolicy(GetPolicyRequest request,
                          StreamObserver<GetPolicyResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        log.info("Received gRPC policy lookup for policyId={} from callerService={}",
                request.getPolicyId(), "claims-service");

        try {
            UUID policyId = UUID.fromString(request.getPolicyId());

            var policyOpt = policyService.findById(policyId);
            if (policyOpt.isEmpty()) {
                outcome = "not_found";
                incrementCounter("GetPolicy", outcome);
                responseObserver.onError(
                        Status.NOT_FOUND
                                .withDescription("Policy not found: " + request.getPolicyId())
                                .asRuntimeException()
                );
                return;
            }

            var domainPolicy = policyOpt.get();

            com.example.policies.grpc.Policy protoPolicy =
                    com.example.policies.grpc.Policy.newBuilder()
                            .setId(domainPolicy.getId().toString())
                            .setPolicyNumber(domainPolicy.getPolicyNumber())
                            .setProductCode(domainPolicy.getProductCode())
                            .setStatus(domainPolicy.getStatus().name())
                            .setValidFrom(domainPolicy.getValidFrom().toString())
                            .setValidTo(domainPolicy.getValidTo().toString())
                            .build();

            GetPolicyResponse response = GetPolicyResponse.newBuilder()
                    .setPolicy(protoPolicy)
                    .build();

            incrementCounter("GetPolicy", "success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            outcome = "invalid_argument";
            incrementCounter("GetPolicy", outcome);
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid policyId: " + request.getPolicyId())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("GetPolicy", outcome);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Unexpected error in getPolicy")
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "GetPolicy", outcome);
        }
    }
}
