package com.example.claims.infrastructure.policy;

import java.time.LocalDate;
import java.util.UUID;

public record PolicySummary(
        UUID id,
        String policyNumber,
        String productCode,
        String status,
        LocalDate validFrom,
        LocalDate validTo
) {
}
