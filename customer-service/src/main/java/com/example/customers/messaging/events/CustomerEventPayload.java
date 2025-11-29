package com.example.customers.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEventPayload {

    private String eventId;
    private CustomerEventType eventType;
    private Instant occurredAt;

    private String customerId;
    private String customerNumber;

    private boolean addressComplete;
    private boolean contactDataComplete;
    private boolean customerDataValid;
}
