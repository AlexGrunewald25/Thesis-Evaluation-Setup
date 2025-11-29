package com.example.claims.infrastructure.policy;

import com.example.policies.grpc.GetPolicyRequest;
import com.example.policies.grpc.GetPolicyResponse;
import com.example.policies.grpc.PolicyServiceGrpc;
import com.example.policies.grpc.Policy;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@Profile("grpc")
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

        // 1) Request mit gesetzter policyId aufbauen
        GetPolicyRequest request = GetPolicyRequest.newBuilder()
                .setPolicyId(policyId.toString())
                .build();

        try {
            log.info("GrpcPolicyClient.getPolicyById({}) called", policyId);

            // 2) gRPC-Aufruf durchführen
            GetPolicyResponse response = stub.getPolicy(request);

            // 3) Falls der Server keine Policy zurückliefert → Optional.empty()
            if (!response.hasPolicy()) {
                log.warn("GrpcPolicyClient.getPolicyById({}) received response without policy", policyId);
                return Optional.empty();
            }

            Policy policy = response.getPolicy();

            // 4) Strings der Datumsfelder defensiv in LocalDate parsen
            String validFromStr = policy.getValidFrom();
            String validToStr = policy.getValidTo();

            LocalDate validFrom = (validFromStr == null || validFromStr.isBlank())
                    ? null
                    : LocalDate.parse(validFromStr);

            LocalDate validTo = (validToStr == null || validToStr.isBlank())
                    ? null
                    : LocalDate.parse(validToStr);

            // 5) PolicySummary aus gRPC-Response bauen
            PolicySummary summary = new PolicySummary(
                    UUID.fromString(policy.getId()),
                    policy.getPolicyNumber(),
                    policy.getProductCode(),
                    policy.getStatus(),
                    validFrom,
                    validTo
            );

            log.info(
                    "GrpcPolicyClient.getPolicyById({}) received policyNumber={} from PolicyService (gRPC)",
                    policyId,
                    summary.policyNumber()
            );

            return Optional.of(summary);

        } catch (StatusRuntimeException ex) {
            log.error("gRPC error in GrpcPolicyClient for policyId {}: {}", policyId, ex.getStatus(), ex);
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
