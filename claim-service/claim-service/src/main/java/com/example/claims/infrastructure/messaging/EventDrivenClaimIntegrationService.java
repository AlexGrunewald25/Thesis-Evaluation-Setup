package com.example.claims.infrastructure.messaging;

import com.example.claims.application.ClaimIntegrationService;
import com.example.claims.domain.Claim;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * EventDrivenClaimIntegrationService nutzt den ClaimEventsProducer,
 * um für jede relevante Statusänderung ein Claim-Event auf Kafka zu publizieren.
 *
 * Aktiv, wenn das Spring-Profil "event-driven" gesetzt ist.
 */
@Slf4j
@Service
@Profile("event-driven")
@RequiredArgsConstructor
public class EventDrivenClaimIntegrationService implements ClaimIntegrationService {

    private final ClaimEventsProducer claimEventsProducer;

    @Override
    public void onClaimSubmitted(Claim claim) {
        log.info(
                "EventDrivenClaimIntegrationService.onClaimSubmitted: "
                        + "Publishing CLAIM_SUBMITTED event for claimId={} policyId={} customerId={} reportedAmount={}",
                claim.getId(),
                claim.getPolicyId(),
                claim.getCustomerId(),
                safeAmount(claim.getReportedAmount())
        );

        if (log.isDebugEnabled()) {
            log.debug("CLAIM_SUBMITTED payload details: status={} approved={} approvedAmount={} decisionReason={}",
                    claim.getStatus(),
                    claim.isApproved(),
                    safeAmount(claim.getApprovedAmount()),
                    claim.getDecisionReason());
        }

        claimEventsProducer.publishClaimSubmitted(claim);
    }

    @Override
    public void onClaimInReview(Claim claim) {
        log.info(
                "EventDrivenClaimIntegrationService.onClaimInReview: "
                        + "Publishing CLAIM_IN_REVIEW event for claimId={} policyId={} customerId={}",
                claim.getId(),
                claim.getPolicyId(),
                claim.getCustomerId()
        );

        if (log.isDebugEnabled()) {
            log.debug("CLAIM_IN_REVIEW payload details: status={} reportedAmount={} createdAt={} lastUpdatedAt={}",
                    claim.getStatus(),
                    safeAmount(claim.getReportedAmount()),
                    claim.getCreatedAt(),
                    claim.getLastUpdatedAt());
        }

        claimEventsProducer.publishClaimInReview(claim);
    }

    @Override
    public void onClaimApproved(Claim claim) {
        log.info(
                "EventDrivenClaimIntegrationService.onClaimApproved: "
                        + "Publishing CLAIM_APPROVED event for claimId={} policyId={} customerId={} approvedAmount={}",
                claim.getId(),
                claim.getPolicyId(),
                claim.getCustomerId(),
                safeAmount(claim.getApprovedAmount())
        );

        if (log.isDebugEnabled()) {
            log.debug("CLAIM_APPROVED payload details: status={} decisionReason={} reportedAmount={} createdAt={} lastUpdatedAt={}",
                    claim.getStatus(),
                    claim.getDecisionReason(),
                    safeAmount(claim.getReportedAmount()),
                    claim.getCreatedAt(),
                    claim.getLastUpdatedAt());
        }

        claimEventsProducer.publishClaimApproved(claim);
    }

    @Override
    public void onClaimRejected(Claim claim) {
        log.info(
                "EventDrivenClaimIntegrationService.onClaimRejected: "
                        + "Publishing CLAIM_REJECTED event for claimId={} policyId={} customerId={}",
                claim.getId(),
                claim.getPolicyId(),
                claim.getCustomerId()
        );

        if (log.isDebugEnabled()) {
            log.debug("CLAIM_REJECTED payload details: status={} decisionReason={} reportedAmount={} createdAt={} lastUpdatedAt={}",
                    claim.getStatus(),
                    claim.getDecisionReason(),
                    safeAmount(claim.getReportedAmount()),
                    claim.getCreatedAt(),
                    claim.getLastUpdatedAt());
        }

        claimEventsProducer.publishClaimRejected(claim);
    }

    @Override
    public void onClaimPaidOut(Claim claim) {
        log.info(
                "EventDrivenClaimIntegrationService.onClaimPaidOut: "
                        + "Publishing CLAIM_PAID_OUT event for claimId={} policyId={} customerId={} approvedAmount={}",
                claim.getId(),
                claim.getPolicyId(),
                claim.getCustomerId(),
                safeAmount(claim.getApprovedAmount())
        );

        if (log.isDebugEnabled()) {
            log.debug("CLAIM_PAID_OUT payload details: status={} reportedAmount={} decisionReason={} createdAt={} lastUpdatedAt={}",
                    claim.getStatus(),
                    safeAmount(claim.getReportedAmount()),
                    claim.getDecisionReason(),
                    claim.getCreatedAt(),
                    claim.getLastUpdatedAt());
        }

        claimEventsProducer.publishClaimPaidOut(claim);
    }

    /**
     * Hilfsmethode, um Nullwerte bei Beträgen im Log sauber zu behandeln.
     */
    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
