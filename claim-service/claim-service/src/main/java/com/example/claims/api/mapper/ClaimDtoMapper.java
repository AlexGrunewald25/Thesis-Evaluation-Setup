package com.example.claims.api.mapper;

import com.example.claims.api.dto.ClaimCreateRequest;
import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Mapper zwischen REST-DTOs und der Domain-Klasse {@link Claim}.
 */
@Component
public class ClaimDtoMapper {

    /**
     * Mappt ein {@link ClaimCreateRequest} in ein Domain-Objekt {@link Claim}.
     * Initialisiert Status, Timestamps und Default-Felder.
     */
    public Claim toDomain(ClaimCreateRequest request) {
        var now = OffsetDateTime.now();

        return Claim.builder()
                .policyId(request.getPolicyId())
                .customerId(request.getCustomerId())
                .description(request.getDescription())
                // reportedAmount ist im Request BigDecimal und in der Domain ebenfalls BigDecimal
                .reportedAmount(request.getReportedAmount())
                .status(ClaimStatus.SUBMITTED)
                .approved(false)
                .approvedAmount(null)
                .decisionReason(null)
                .createdAt(now)
                .lastUpdatedAt(now)
                .build();
    }

    /**
     * Mappt ein Domain-Objekt {@link Claim} in das REST-Response-DTO {@link ClaimResponse}.
     */
    public ClaimResponse toResponse(Claim claim) {
        return ClaimResponse.builder()
                .id(claim.getId())
                .policyId(claim.getPolicyId())
                .customerId(claim.getCustomerId())
                .description(claim.getDescription())
                // Domain: BigDecimal -> DTO: double
                .reportedAmount(
                        claim.getReportedAmount() != null
                                ? claim.getReportedAmount().doubleValue()
                                : 0.0
                )
                .status(claim.getStatus())
                .approved(claim.isApproved())
                // Domain: BigDecimal -> DTO: Double (nullable)
                .approvedAmount(
                        claim.getApprovedAmount() != null
                                ? claim.getApprovedAmount().doubleValue()
                                : null
                )
                .decisionReason(claim.getDecisionReason())
                .createdAt(claim.getCreatedAt())
                .lastUpdatedAt(claim.getLastUpdatedAt())
                .build();
    }
}
