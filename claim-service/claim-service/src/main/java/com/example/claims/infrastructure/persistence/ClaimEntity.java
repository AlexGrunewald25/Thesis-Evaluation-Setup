package com.example.claims.infrastructure.persistence;

import com.example.claims.domain.ClaimStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "reported_amount", nullable = false)
    private double reportedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ClaimStatus status;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Column(name = "approved_amount")
    private Double approvedAmount;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;
}
