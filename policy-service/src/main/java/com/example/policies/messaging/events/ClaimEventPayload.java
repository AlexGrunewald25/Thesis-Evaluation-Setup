package com.example.policies.messaging.events;

import com.example.policies.domain.PolicyStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ClaimEventPayload {

    private UUID eventId;
    private ClaimEventType eventType;

    private UUID claimId;
    private UUID policyId;
    private UUID customerId;
    private String description;

    private BigDecimal reportedAmount;
    private String status;               // oder eigenes Enum, wenn du ClaimStatus spiegeln willst
    private boolean approved;
    private BigDecimal approvedAmount;
    private String decisionReason;

    private OffsetDateTime createdAt;
    private OffsetDateTime lastUpdatedAt;

    // Optionale Convenience-Felder für Policy-Checks, wenn du magst:
    // private boolean covered;
    // private String coverageType;
}
