package com.example.claims.support.error;

/**
 * Wird geworfen, wenn ein ungültiger Zustandsübergang
 * im Lebenszyklus eines Claim ausgeführt werden soll.
 */
public class InvalidClaimStateException extends RuntimeException {

    public InvalidClaimStateException(String message) {
        super(message);
    }
}
