package com.example.claims.infrastructure.customer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * No-Op Implementierung des CustomerClient für das event-getriebene Profil.
 *
 * - Wird aktiv, wenn das Profil "event-driven" aktiv ist.
 * - Es findet kein synchroner Aufruf des Customer-Service statt.
 */
@Slf4j
@Component
@Profile({"event-driven"})
public class NoOpCustomerClient implements CustomerClient {

    @Override
    public boolean isCustomerDataValid(UUID customerId) {
        log.info("NoOpCustomerClient active (event-driven mode) – skipping synchronous customer validation for customerId={}",
                customerId);
        return false;
    }
}
