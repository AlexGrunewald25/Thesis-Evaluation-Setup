package com.example.claims.infrastructure.policy;

import java.util.Optional;
import java.util.UUID;

public interface PolicyClient {

    Optional<PolicySummary> getPolicyById(UUID policyId);
}
