package com.example.claims.infrastructure.messaging;

import com.example.claims.application.ClaimIntegrationService;
import com.example.claims.domain.Claim;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * EventDrivenClaimIntegrationService nutzt den ClaimEventsProducer,
 * um für jede relevante Statusänderung ein Claim-Event auf Kafka zu publizieren.
 *
 * Aktiv, wenn das Spring-Profil "event-driven" gesetzt ist.
 */
@Service
@Profile("event-driven")
@RequiredArgsConstructor
public class EventDrivenClaimIntegrationService implements ClaimIntegrationService {

    private final ClaimEventsProducer claimEventsProducer;

    @Override
    public void onClaimSubmitted(Claim claim) {
        claimEventsProducer.publishClaimSubmitted(claim);
    }

    @Override
    public void onClaimInReview(Claim claim) {
        claimEventsProducer.publishClaimInReview(claim);
    }

    @Override
    public void onClaimApproved(Claim claim) {
        claimEventsProducer.publishClaimApproved(claim);
    }

    @Override
    public void onClaimRejected(Claim claim) {
        claimEventsProducer.publishClaimRejected(claim);
    }

    @Override
    public void onClaimPaidOut(Claim claim) {
        claimEventsProducer.publishClaimPaidOut(claim);
    }
}
