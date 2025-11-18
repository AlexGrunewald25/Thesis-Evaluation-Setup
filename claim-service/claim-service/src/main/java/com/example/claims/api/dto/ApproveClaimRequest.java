package com.example.claims.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveClaimRequest {

    @NotNull
    @Min(0)
    private Double approvedAmount;

    private String reason;
}
