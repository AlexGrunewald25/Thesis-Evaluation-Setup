package com.example.claims.infrastructure.messaging;

import com.example.claims.domain.Claim;
import com.example.claims.infrastructure.messaging.events.ClaimEventPayload;
import com.example.claims.infrastructure.messaging.events.ClaimEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Verantwortlich für das Publizieren von Claim-bezogenen Domain-Events über Kafka.
 * Zusätzlich werden eigene Metriken für Latenz und Erfolgs-/Fehlerquoten erfasst.
 */
@Service
@RequiredArgsConstructor
public class ClaimEventsProducer {

    private static final String DEFAULT_TOPIC = "claims.claim-events";

    private final KafkaTemplate<String, ClaimEventPayload> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // -------------------------------------------------------------------------
    // Helper für Metriken
    // -------------------------------------------------------------------------

    private Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    private void stopSample(Timer.Sample sample, String name, String eventType, String outcome) {
        sample.stop(
                Timer.builder(name)
                        .description("Kafka producer latency for claim events")
                        .tag("eventType", eventType)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementCounter(String name, String eventType, String outcome) {
        Counter.builder(name)
                .description("Kafka records produced for claim events")
                .tag("eventType", eventType)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    // -------------------------------------------------------------------------
    // API-Methoden
    // -------------------------------------------------------------------------

    public void publishClaimSubmitted(Claim claim) {
        publishEvent(claim, ClaimEventType.CLAIM_SUBMITTED);
    }

    public void publishClaimInReview(Claim claim) {
        publishEvent(claim, ClaimEventType.CLAIM_IN_REVIEW);
    }

    public void publishClaimApproved(Claim claim) {
        publishEvent(claim, ClaimEventType.CLAIM_APPROVED);
    }

    public void publishClaimRejected(Claim claim) {
        publishEvent(claim, ClaimEventType.CLAIM_REJECTED);
    }

    public void publishClaimPaidOut(Claim claim) {
        publishEvent(claim, ClaimEventType.CLAIM_PAID_OUT);
    }

    // -------------------------------------------------------------------------
    // Zentrale Publizier-Logik mit Metriken
    // -------------------------------------------------------------------------

    private void publishEvent(Claim claim, ClaimEventType eventType) {
        var now = OffsetDateTime.now();

        var payload = ClaimEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .occurredAt(now)
                .claimId(claim.getId())
                .policyId(claim.getPolicyId())
                .customerId(claim.getCustomerId())
                .description(claim.getDescription())
                .reportedAmount(claim.getReportedAmount())        // BigDecimal
                .status(claim.getStatus())
                .approved(claim.isApproved())
                .approvedAmount(claim.getApprovedAmount())        // BigDecimal, kann null sein
                .decisionReason(claim.getDecisionReason())
                .createdAt(claim.getCreatedAt())
                .lastUpdatedAt(claim.getLastUpdatedAt())
                .build();

        Timer.Sample sample = startSample();
        String eventTypeName = eventType.name();

        try {
            kafkaTemplate.send(DEFAULT_TOPIC, claim.getId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            // erfolgreich gesendet
                            stopSample(sample, "claims.kafka.producer.latency", eventTypeName, "success");
                            incrementCounter("claims.kafka.producer.records", eventTypeName, "success");
                        } else {
                            // asynchroner Fehler (Broker down, Timeout etc.)
                            stopSample(sample, "claims.kafka.producer.latency", eventTypeName, "error");
                            incrementCounter("claims.kafka.producer.records", eventTypeName, "error");
                        }
                    });
        } catch (Exception ex) {
            // wirklich synchroner Fehler (z.B. Serialisierung vor dem Senden)
            stopSample(sample, "claims.kafka.producer.latency", eventTypeName, "exception");
            incrementCounter("claims.kafka.producer.records", eventTypeName, "exception");
            throw ex;
        }
    }
}
