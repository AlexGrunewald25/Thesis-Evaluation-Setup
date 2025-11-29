package com.example.claims.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerValidationResultPayload {

    private UUID eventId;
    private CustomerValidationResultType eventType;
    private Instant occurredAt;

    private UUID claimId;

    private UUID customerId;
    private String customerNumber;

    private boolean addressComplete;
    private boolean contactDataComplete;
    private boolean customerDataValid;
}
