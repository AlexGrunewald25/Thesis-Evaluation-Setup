package com.example.policies.domain;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class Policy {

    UUID id;
    String policyNumber;
    String productCode;
    PolicyStatus status;
    LocalDate validFrom;
    LocalDate validTo;
}
