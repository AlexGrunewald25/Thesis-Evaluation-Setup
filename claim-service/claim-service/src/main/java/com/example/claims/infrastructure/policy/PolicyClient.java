package com.example.claims.infrastructure.policy;

import java.util.Optional;
import java.util.UUID;

public interface PolicyClient {

    /**
     * Lädt eine Policy-Summary für die gegebene Policy-ID.
     *
     * @param policyId Technische ID der Police (UUID).
     * @return Optional mit PolicySummary, leer falls nicht gefunden oder Fehler.
     */
    Optional<PolicySummary> getPolicyById(UUID policyId);
}
