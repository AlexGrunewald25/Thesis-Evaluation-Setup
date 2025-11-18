package com.example.claims.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateClaimRequest {

    @NotNull
    private UUID policyId;

    @NotNull
    private UUID customerId;

    @NotBlank
    private String description;

    @Min(0)
    private double reportedAmount;
}
