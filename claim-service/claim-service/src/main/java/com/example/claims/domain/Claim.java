package com.example.claims.domain;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.example.claims.support.error.InvalidClaimStateException;

@Data
@Builder
public class Claim {

    private UUID id;
    private UUID policyId;
    private UUID customerId;
    private String description;
    private BigDecimal reportedAmount;

    private ClaimStatus status;
    private boolean approved;
    private BigDecimal approvedAmount;
    private String decisionReason;

    private OffsetDateTime createdAt;
    private OffsetDateTime lastUpdatedAt;

    /**
     * Übergang SUBMITTED -> IN_REVIEW
     */
    public void startReview() {
        if (status != ClaimStatus.SUBMITTED) {
            throw new InvalidClaimStateException(
                    "Claim " + id + " can only enter IN_REVIEW from SUBMITTED state, but was " + status
            );
        }
        this.status = ClaimStatus.IN_REVIEW;
        this.lastUpdatedAt = OffsetDateTime.now();
    }

    /**
     * Übergang IN_REVIEW -> APPROVED
     */
    public void approve(BigDecimal approvedAmount, String decisionReason) {
        if (status != ClaimStatus.IN_REVIEW) {
            throw new InvalidClaimStateException(
                    "Claim " + id + " can only be approved from IN_REVIEW state, but was " + status
            );
        }
        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Approved amount must be non-negative");
        }

        this.status = ClaimStatus.APPROVED;
        this.approved = true;
        this.approvedAmount = approvedAmount;
        this.decisionReason = decisionReason;
        this.lastUpdatedAt = OffsetDateTime.now();
    }

    /**
     * Übergang IN_REVIEW -> REJECTED
     */
    public void reject(String decisionReason) {
        if (status != ClaimStatus.IN_REVIEW) {
            throw new InvalidClaimStateException(
                    "Claim " + id + " can only be rejected from IN_REVIEW state, but was " + status
            );
        }
        this.status = ClaimStatus.REJECTED;
        this.approved = false;
        this.approvedAmount = BigDecimal.ZERO;
        this.decisionReason = decisionReason;
        this.lastUpdatedAt = OffsetDateTime.now();
    }

    /**
     * Übergang APPROVED -> PAID_OUT
     */
    public void payout() {
        if (status != ClaimStatus.APPROVED) {
            throw new InvalidClaimStateException(
                    "Claim " + id + " can only be paid out from APPROVED state, but was " + status
            );
        }
        this.status = ClaimStatus.PAID_OUT;
        this.lastUpdatedAt = OffsetDateTime.now();
    }
}
