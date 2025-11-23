package com.example.claims.infrastructure.persistence;

import com.example.claims.domain.Claim;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ClaimEntityMapper {

    public ClaimEntity toEntity(Claim claim) {
        if (claim == null) {
            return null;
        }

        double reportedAmount = claim.getReportedAmount() != null
                ? claim.getReportedAmount().doubleValue()
                : 0.0;

        Double approvedAmount = claim.getApprovedAmount() != null
                ? claim.getApprovedAmount().doubleValue()
                : null;

        return ClaimEntity.builder()
                .id(claim.getId())
                .policyId(claim.getPolicyId())
                .customerId(claim.getCustomerId())
                .description(claim.getDescription())
                .reportedAmount(reportedAmount)
                .status(claim.getStatus())
                .approved(claim.isApproved())
                .approvedAmount(approvedAmount)
                .decisionReason(claim.getDecisionReason())
                .createdAt(claim.getCreatedAt())
                .lastUpdatedAt(claim.getLastUpdatedAt())
                .build();
    }

    public Claim toDomain(ClaimEntity entity) {
        if (entity == null) {
            return null;
        }

        BigDecimal reportedAmount = BigDecimal.valueOf(entity.getReportedAmount());

        BigDecimal approvedAmount = entity.getApprovedAmount() != null
                ? BigDecimal.valueOf(entity.getApprovedAmount())
                : null;

        return Claim.builder()
                .id(entity.getId())
                .policyId(entity.getPolicyId())
                .customerId(entity.getCustomerId())
                .description(entity.getDescription())
                .reportedAmount(reportedAmount)
                .status(entity.getStatus())
                .approved(entity.isApproved())
                .approvedAmount(approvedAmount)
                .decisionReason(entity.getDecisionReason())
                .createdAt(entity.getCreatedAt())
                .lastUpdatedAt(entity.getLastUpdatedAt())
                .build();
    }
}
