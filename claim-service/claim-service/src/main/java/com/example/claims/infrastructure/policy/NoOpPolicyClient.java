package com.example.claims.infrastructure.policy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * No-Op Implementierung des PolicyClient für das event-getriebene Profil.
 *
 * Hintergrund:
 * - ClaimServiceImpl erwartet immer einen PolicyClient im Konstruktor.
 * - Im Profil "event-driven" findet kein synchroner Policy-Lookup statt.
 * - Diese Implementierung stellt sicher, dass der Bean vorhanden ist,
 *   ohne eine externe Abhängigkeit aufzubauen.
 */
@Slf4j
@Component
@Profile({"event-driven", "kafka"})
public class NoOpPolicyClient implements PolicyClient {

    @Override
    public Optional<PolicySummary> getPolicyById(UUID policyId) {
        log.info("NoOpPolicyClient active (event-driven mode) – skipping synchronous policy lookup for policyId={}", policyId);
        return Optional.empty();
    }
}
