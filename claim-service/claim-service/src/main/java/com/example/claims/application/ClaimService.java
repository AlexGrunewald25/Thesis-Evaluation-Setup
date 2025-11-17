package com.example.claims.application;

import com.example.claims.domain.Claim;

import java.util.List;
import java.util.UUID;

public interface ClaimService {

    Claim submitClaim(UUID policyId,
                      UUID customerId,
                      String description,
                      double reportedAmount);

    Claim getClaim(UUID claimId);

    List<Claim> getClaimsForCustomer(UUID customerId);

    Claim approveClaim(UUID claimId,
                       double approvedAmount,
                       String reason);

    Claim rejectClaim(UUID claimId,
                      String reason);

    Claim markAsPaidOut(UUID claimId);
}
