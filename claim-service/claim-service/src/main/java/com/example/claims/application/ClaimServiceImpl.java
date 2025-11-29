package com.example.claims.application;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.application.ClaimIntegrationService;
import com.example.claims.infrastructure.persistence.ClaimEntity;
import com.example.claims.infrastructure.persistence.ClaimEntityMapper;
import com.example.claims.infrastructure.persistence.ClaimJpaRepository;
import com.example.claims.infrastructure.policy.PolicyClient;
import com.example.claims.support.error.ClaimNotFoundException;
import com.example.claims.support.error.InvalidClaimStateException;
import io.micrometer.core.instrument.MeterRegistry;
import com.example.claims.infrastructure.customer.CustomerClient;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ClaimServiceImpl implements ClaimService {

    private final ClaimJpaRepository claimRepository;
    private final ClaimEntityMapper claimEntityMapper;
    private final ClaimIntegrationService claimIntegrationService;
    private final MeterRegistry meterRegistry;
    private final PolicyClient policyClient;
    private final CustomerClient customerClient;

    // Helper: Timer für eine Operation mit Tag "operation"
    private Timer timer(String operation) {
        return Timer.builder("claims_service_operation_duration")
                .description("Duration of claim service operations")
                .tag("operation", operation)
                .publishPercentileHistogram(true)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // Helper: Counter für eine Operation mit Tag "operation"
    private void incrementCounter(String operation) {
        meterRegistry.counter("claims_service_operation_total",
                        "operation", operation)
                .increment();
    }

    private void incrementPolicyLookupCounter(String outcome) {
        meterRegistry.counter("claims_policy_lookup_total",
                        "outcome", outcome)
                .increment();
    }

    private void incrementCustomerValidationCounter(String outcome) {
        meterRegistry.counter("claims_customer_validation_total",
                        "outcome", outcome)
                .increment();
    }

    @Override
    public Claim submitClaim(UUID policyId,
                             UUID customerId,
                             String description,
                             BigDecimal reportedAmount) {

        return timer("submit").record(() -> {
            incrementCounter("submit");

            // -----------------------------------------------------------------
            // 1) Policy-Lookup über PolicyService
            // -----------------------------------------------------------------
            log.info("ClaimServiceImpl.submitClaim: calling PolicyService for policyId={}", policyId);

            policyClient.getPolicyById(policyId)
                    .ifPresentOrElse(
                            policy -> {
                                incrementPolicyLookupCounter("found");
                                log.info("PolicyService returned policy {} for policyId={}",
                                        policy.policyNumber(), policyId);
                            },
                            () -> {
                                incrementPolicyLookupCounter("not_found");
                                log.warn("PolicyService did not return a policy for policyId={}", policyId);
                            }
                    );

            // -----------------------------------------------------------------
            // 2) Customer-Validierung über CustomerService
            // -----------------------------------------------------------------
            log.info("ClaimServiceImpl.submitClaim: calling CustomerService for customerId={}", customerId);

            boolean customerValid = customerClient.isCustomerDataValid(customerId);

            if (customerValid) {
                incrementCustomerValidationCounter("valid");
                log.info("CustomerService reports valid customer data for customerId={}", customerId);
            } else {
                incrementCustomerValidationCounter("invalid_or_not_found");
                log.warn("CustomerService reports invalid or missing customer data for customerId={}", customerId);
            }

            // -----------------------------------------------------------------
            // 3) Claim-Domainobjekt erzeugen und speichern
            // -----------------------------------------------------------------
            var now = OffsetDateTime.now();

            Claim claim = Claim.builder()
                    .id(UUID.randomUUID())
                    .policyId(policyId)
                    .customerId(customerId)
                    .description(description)
                    .reportedAmount(reportedAmount)
                    .status(ClaimStatus.SUBMITTED)
                    .approved(false)
                    .approvedAmount(null)
                    .decisionReason(null)
                    .createdAt(now)
                    .lastUpdatedAt(now)
                    .build();

            ClaimEntity entity = claimEntityMapper.toEntity(claim);
            ClaimEntity saved = claimRepository.save(entity);

            Claim result = claimEntityMapper.toDomain(saved);

            // event-driven Integration (Kafka) oder No-Op – je nach Profil
            claimIntegrationService.onClaimSubmitted(result);

            return result;
        });
    }

    @Override
    public Claim startReview(UUID claimId) {
        return timer("startReview").record(() -> {
            incrementCounter("startReview");

            ClaimEntity entity = claimRepository.findById(claimId)
                    .orElseThrow(() -> new ClaimNotFoundException(claimId));

            Claim domain = claimEntityMapper.toDomain(entity);
            domain.startReview();

            ClaimEntity updated = claimEntityMapper.toEntity(domain);
            updated = claimRepository.save(updated);

            Claim result = claimEntityMapper.toDomain(updated);

            claimIntegrationService.onClaimInReview(result);
            return result;
        });
    }

    @Override
    public Claim approveClaim(UUID claimId, BigDecimal approvedAmount, String decisionReason) {
        return timer("approve").record(() -> {
            incrementCounter("approve");

            ClaimEntity entity = claimRepository.findById(claimId)
                    .orElseThrow(() -> new ClaimNotFoundException(claimId));

            Claim domain = claimEntityMapper.toDomain(entity);
            domain.approve(approvedAmount, decisionReason);

            ClaimEntity updated = claimEntityMapper.toEntity(domain);
            updated = claimRepository.save(updated);

            Claim result = claimEntityMapper.toDomain(updated);
            claimIntegrationService.onClaimApproved(result);
            return result;
        });
    }

    @Override
    public Claim rejectClaim(UUID claimId, String decisionReason) {
        return timer("reject").record(() -> {
            incrementCounter("reject");

            ClaimEntity entity = claimRepository.findById(claimId)
                    .orElseThrow(() -> new ClaimNotFoundException(claimId));

            Claim domain = claimEntityMapper.toDomain(entity);
            domain.reject(decisionReason);

            ClaimEntity updated = claimEntityMapper.toEntity(domain);
            updated = claimRepository.save(updated);

            Claim result = claimEntityMapper.toDomain(updated);
            claimIntegrationService.onClaimRejected(result);
            return result;
        });
    }

    @Override
    public Claim payoutClaim(UUID claimId) {
        return timer("payout").record(() -> {
            incrementCounter("payout");

            ClaimEntity entity = claimRepository.findById(claimId)
                    .orElseThrow(() -> new ClaimNotFoundException(claimId));

            Claim domain = claimEntityMapper.toDomain(entity);
            domain.payout();

            ClaimEntity updated = claimEntityMapper.toEntity(domain);
            updated = claimRepository.save(updated);

            Claim result = claimEntityMapper.toDomain(updated);
            claimIntegrationService.onClaimPaidOut(result);
            return result;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Claim getClaimById(UUID claimId) {
        incrementCounter("getById");

        return claimRepository.findById(claimId)
                .map(claimEntityMapper::toDomain)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Claim> getClaimsForCustomer(UUID customerId) {
        incrementCounter("getForCustomer");

        return claimRepository.findByCustomerId(customerId).stream()
                .map(claimEntityMapper::toDomain)
                .toList();
    }
}
