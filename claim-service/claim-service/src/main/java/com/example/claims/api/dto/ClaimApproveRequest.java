package com.example.claims.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ClaimApproveRequest {

    @NotNull
    @Min(0)
    private BigDecimal approvedAmount;

    private String reason;
}
