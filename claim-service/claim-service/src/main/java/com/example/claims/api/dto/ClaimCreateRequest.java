package com.example.claims.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request-DTO für das Anlegen eines neuen Schadenfalls (Claim)
 * über die REST-API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimCreateRequest {

    @NotNull
    private UUID policyId;

    @NotNull
    private UUID customerId;

    @NotNull
    @Size(min = 5, max = 500)
    private String description;

    @NotNull
    @Positive
    private BigDecimal reportedAmount;
}
