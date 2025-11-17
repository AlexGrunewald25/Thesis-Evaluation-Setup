package com.example.claims.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class Claim {

    private final UUID id;
    private final UUID policyId;
    private final UUID customerId;

    private String description;
    private double reportedAmount;

    private ClaimStatus status;

    private boolean approved;
    private Double approvedAmount;
    private String decisionReason;

    private final OffsetDateTime createdAt;
    private OffsetDateTime lastUpdatedAt;

    public static Claim newSubmittedClaim(UUID policyId,
                                          UUID customerId,
                                          String description,
                                          double reportedAmount,
                                          OffsetDateTime now) {
        return Claim.builder()
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
    }

    public void markInReview(OffsetDateTime now) {
        this.status = ClaimStatus.IN_REVIEW;
        this.lastUpdatedAt = now;
    }

    public void approve(double approvedAmount, String reason, OffsetDateTime now) {
        this.status = ClaimStatus.APPROVED;
        this.approved = true;
        this.approvedAmount = approvedAmount;
        this.decisionReason = reason;
        this.lastUpdatedAt = now;
    }

    public void reject(String reason, OffsetDateTime now) {
        this.status = ClaimStatus.REJECTED;
        this.approved = false;
        this.approvedAmount = null;
        this.decisionReason = reason;
        this.lastUpdatedAt = now;
    }

    public void markPaidOut(OffsetDateTime now) {
        this.status = ClaimStatus.PAID_OUT;
        this.lastUpdatedAt = now;
    }
}
