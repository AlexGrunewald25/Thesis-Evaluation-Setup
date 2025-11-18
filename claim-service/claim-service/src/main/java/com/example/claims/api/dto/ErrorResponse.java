package com.example.claims.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

/**
 * Standardisiertes Fehlerobjekt für REST-Antworten.
 */
@Value
@Builder
public class ErrorResponse {

    OffsetDateTime timestamp;
    int status;
    String error;
    String message;
    String path;
}
