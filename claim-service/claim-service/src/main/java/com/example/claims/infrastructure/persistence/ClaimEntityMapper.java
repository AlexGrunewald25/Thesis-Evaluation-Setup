package com.example.claims.infrastructure.persistence;

import com.example.claims.domain.Claim;

public final class ClaimEntityMapper {

    private ClaimEntityMapper() {
    }

    public static ClaimEntity toEntity(Claim claim) {
        if (claim == null) {
            return null;
        }
        return ClaimEntity.builder()
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

    public static Claim toDomain(ClaimEntity entity) {
        if (entity == null) {
            return null;
        }
        return Claim.builder()
                .id(entity.getId())
                .policyId(entity.getPolicyId())
                .customerId(entity.getCustomerId())
                .description(entity.getDescription())
                .reportedAmount(entity.getReportedAmount())
                .status(entity.getStatus())
                .approved(entity.isApproved())
                .approvedAmount(entity.getApprovedAmount())
                .decisionReason(entity.getDecisionReason())
                .createdAt(entity.getCreatedAt())
                .lastUpdatedAt(entity.getLastUpdatedAt())
                .build();
    }
}
