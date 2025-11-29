package com.example.claims.infrastructure.customer;

import java.util.UUID;

public interface CustomerClient {

    /**
     * Führt eine Validierung der Kundendaten für den gegebenen Customer durch.
     *
     * @param customerId Technische ID des Kunden (UUID), wie sie im Claim gespeichert ist.
     * @return true, wenn die Kundendaten vollständig/valide sind, ansonsten false.
     */
    boolean isCustomerDataValid(UUID customerId);
}
