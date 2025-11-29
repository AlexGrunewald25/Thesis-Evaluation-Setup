package com.example.policies.infrastructure.rest;

import com.example.policies.domain.Policy;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST-DTO für die Darstellung einer Police.
 * Entkoppelt die externe Repräsentation von der Domain-Entität.
 */
public record PolicyResponseDto(
        UUID id,
        String policyNumber,
        String productCode,
        String status,
        LocalDate validFrom,
        LocalDate validTo
) {

    public static PolicyResponseDto fromDomain(Policy policy) {
        return new PolicyResponseDto(
                policy.getId(),
                policy.getPolicyNumber(),
                policy.getProductCode(),
                policy.getStatus().name(),
                policy.getValidFrom(),
                policy.getValidTo()
        );
    }
}
