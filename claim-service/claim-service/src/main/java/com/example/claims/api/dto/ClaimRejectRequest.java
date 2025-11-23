package com.example.claims.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClaimRejectRequest {

    @NotBlank
    private String reason;
}
