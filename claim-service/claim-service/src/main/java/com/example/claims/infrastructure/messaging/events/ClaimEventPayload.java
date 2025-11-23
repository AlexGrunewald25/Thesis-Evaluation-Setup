package com.example.claims.infrastructure.messaging.events;

import com.example.claims.domain.ClaimStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ClaimEventPayload {

    private UUID eventId;
    private ClaimEventType eventType;
    private OffsetDateTime occurredAt;

    private UUID claimId;
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
}
