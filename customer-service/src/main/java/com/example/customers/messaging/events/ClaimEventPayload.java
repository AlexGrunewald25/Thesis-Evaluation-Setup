package com.example.customers.messaging.events;

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

    // Wichtig für unsere Validierungslogik – werden wir später im Claim-Service befüllen
    private String customerNumber;

    private String description;

    private BigDecimal reportedAmount;
    private String status;
    private boolean approved;
    private BigDecimal approvedAmount;
    private String decisionReason;

    private OffsetDateTime createdAt;
    private OffsetDateTime lastUpdatedAt;
}
