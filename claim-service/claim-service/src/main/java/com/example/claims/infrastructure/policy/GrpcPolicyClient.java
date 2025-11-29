package com.example.claims.infrastructure.policy;

import com.example.policies.grpc.GetPolicyRequest;
import com.example.policies.grpc.PolicyServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * gRPC-basierter PolicyClient für das Profil "grpc-sync".
 *
 * Vorbild: gRPC-Nutzung im Claim-Service (ClaimsGrpcService).
 */
@Slf4j
@Component
@Profile("grpc-sync")
public class GrpcPolicyClient implements PolicyClient, DisposableBean {

    private final ManagedChannel channel;
    private final PolicyServiceGrpc.PolicyServiceBlockingStub stub;

    public GrpcPolicyClient(
            @Value("${policy.grpc.host:localhost}") String host,
            @Value("${policy.grpc.port:9191}") int port) {

        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        this.stub = PolicyServiceGrpc.newBlockingStub(channel);
        log.info("Initialized GrpcPolicyClient for {}:{}", host, port);
    }

    @Override
    public Optional<PolicySummary> getPolicyById(UUID policyId) {
        try {
            GetPolicyRequest request = GetPolicyRequest.newBuilder()
                    .setPolicyId(policyId.toString())
                    .build();
            log.info("GrpcPolicyClient.getPolicyById({}) called", policyId);
            var response = stub.getPolicy(request);

            var p = response.getPolicy();

            PolicySummary summary = new PolicySummary(
                    UUID.fromString(p.getId()),
                    p.getPolicyNumber(),
                    p.getProductCode(),
                    p.getStatus(),
                    LocalDate.parse(p.getValidFrom()),
                    LocalDate.parse(p.getValidTo())
            );


            return Optional.of(summary);
        } catch (StatusRuntimeException ex) {
            Status status = ex.getStatus();
            if (status.getCode() == Status.NOT_FOUND.getCode()) {
                log.warn("Policy {} not found via gRPC", policyId);
                return Optional.empty();
            }

            log.error("Error calling PolicyService via gRPC for policyId {}: status={} description={}",
                    policyId, status.getCode(), status.getDescription());
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Unexpected error in GrpcPolicyClient for policyId {}: {}", policyId, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    @Override
    public void destroy() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
