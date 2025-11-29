package com.example.claims.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyEvaluationResultPayload {

    private UUID eventId;
    private PolicyEvaluationResultType eventType;
    private Instant occurredAt;

    private UUID claimId;
    private UUID policyId;

    private String policyNumber;
    private String productCode;
    private String status;

    private LocalDate validFrom;
    private LocalDate validTo;

    private boolean coverageValid;
}
