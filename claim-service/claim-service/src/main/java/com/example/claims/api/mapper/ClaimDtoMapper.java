package com.example.claims.api.mapper;

import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.domain.Claim;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClaimDtoMapper {

    public static ClaimResponse toResponse(Claim claim) {
        if (claim == null) {
            return null;
        }

        return ClaimResponse.builder()
                .id(claim.getId())
                .policyId(claim.getPolicyId())
                .customerId(claim.getCustomerId())
                .description(claim.getDescription())
                .reportedAmount(claim.getReportedAmount())
                .status(claim.getStatus())
                .approved(claim.isApproved())
                .approvedAmount(claim.getApprovedAmount())
                .decisionReason(claim.getDecisionReason())
                .createdAt(claim.getCreatedAt())
                .lastUpdatedAt(claim.getLastUpdatedAt())
                .build();
    }
}
