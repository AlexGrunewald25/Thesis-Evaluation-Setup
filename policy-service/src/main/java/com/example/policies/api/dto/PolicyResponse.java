package com.example.policies.api.dto;

import com.example.policies.domain.PolicyStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class PolicyResponse {

    UUID id;
    String policyNumber;
    String productCode;
    PolicyStatus status;
    LocalDate validFrom;
    LocalDate validTo;
}
