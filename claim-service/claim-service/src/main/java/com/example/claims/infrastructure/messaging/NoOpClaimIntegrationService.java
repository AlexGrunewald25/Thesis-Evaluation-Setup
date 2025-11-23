package com.example.claims.infrastructure.messaging;

import com.example.claims.application.ClaimIntegrationService;
import com.example.claims.domain.Claim;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * NoOpClaimIntegrationService führt bei Statusänderungen keine Integration
 * mit nachgelagerten Systemen durch.
 *
 * Aktiv in allen Profilen, in denen NICHT das Profil "event-driven" gesetzt ist.
 * Damit ist dies die Standard-Implementierung für rein synchrone Varianten
 * (z.B. REST- oder gRPC-basierte Aufrufer ohne Kafka-Events).
 */
@Service
@Profile("!event-driven")
public class NoOpClaimIntegrationService implements ClaimIntegrationService {

    @Override
    public void onClaimSubmitted(Claim claim) {
        // bewusst keine Aktion
    }

    @Override
    public void onClaimInReview(Claim claim) {
        // bewusst keine Aktion
    }

    @Override
    public void onClaimApproved(Claim claim) {
        // bewusst keine Aktion
    }

    @Override
    public void onClaimRejected(Claim claim) {
        // bewusst keine Aktion
    }

    @Override
    public void onClaimPaidOut(Claim claim) {
        // bewusst keine Aktion
    }
}
