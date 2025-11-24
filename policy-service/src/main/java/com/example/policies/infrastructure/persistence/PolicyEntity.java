package com.example.policies.infrastructure.persistence;

import com.example.policies.domain.PolicyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "policies")
@Getter
@Setter
public class PolicyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PolicyStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;
}
