package com.example.claims.api.dto;

import com.example.claims.domain.ClaimStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
public class ClaimResponse {

    UUID id;
    UUID policyId;
    UUID customerId;

    String description;
    double reportedAmount;

    ClaimStatus status;

    boolean approved;
    Double approvedAmount;
    String decisionReason;

    OffsetDateTime createdAt;
    OffsetDateTime lastUpdatedAt;
}
