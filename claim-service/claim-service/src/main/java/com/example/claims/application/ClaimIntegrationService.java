package com.example.claims.application;

import com.example.claims.domain.Claim;

/**
 * ClaimIntegrationService kapselt die Kommunikation zu nachgelagerten Systemen,
 * die über Statusänderungen eines Claims informiert werden sollen.
 *
 * Je nach aktivem Spring-Profil kann die Implementierung Ereignisse z.B.
 * über Kafka publizieren (event-driven) oder gar nichts tun (rein synchrones Pattern).
 */
public interface ClaimIntegrationService {

    void onClaimSubmitted(Claim claim);

    void onClaimInReview(Claim claim);

    void onClaimApproved(Claim claim);

    void onClaimRejected(Claim claim);

    void onClaimPaidOut(Claim claim);
}
