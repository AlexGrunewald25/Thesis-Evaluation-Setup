package com.example.claims.application;

import com.example.claims.domain.Claim;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ClaimService {

    /**
     * Neuer Schadenfall (Claim) wird erfasst und initial im Status SUBMITTED angelegt.
     */
    Claim submitClaim(UUID policyId,
                      UUID customerId,
                      String description,
                      BigDecimal reportedAmount);

    /**
     * Übergang SUBMITTED -> IN_REVIEW.
     */
    Claim startReview(UUID claimId);

    /**
     * Übergang IN_REVIEW -> APPROVED.
     */
    Claim approveClaim(UUID claimId,
                       BigDecimal approvedAmount,
                       String decisionReason);

    /**
     * Übergang IN_REVIEW -> REJECTED.
     */
    Claim rejectClaim(UUID claimId,
                      String decisionReason);

    /**
     * Übergang APPROVED -> PAID_OUT.
     */
    Claim payoutClaim(UUID claimId);

    /**
     * Einzelnen Claim laden.
     */
    Claim getClaimById(UUID claimId);

    /**
     * Alle Claims eines Kunden laden.
     */
    List<Claim> getClaimsForCustomer(UUID customerId);
}
