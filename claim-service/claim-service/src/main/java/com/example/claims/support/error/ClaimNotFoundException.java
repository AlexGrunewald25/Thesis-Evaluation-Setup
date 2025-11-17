package com.example.claims.support.error;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(UUID claimId) {
        super("Claim with id " + claimId + " not found");
    }
}
