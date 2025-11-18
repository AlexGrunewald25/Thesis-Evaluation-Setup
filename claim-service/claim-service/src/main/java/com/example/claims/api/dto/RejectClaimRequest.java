package com.example.claims.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectClaimRequest {

    @NotBlank
    private String reason;
}
