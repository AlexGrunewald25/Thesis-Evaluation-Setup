package com.example.policies.infrastructure.messaging;

import com.example.policies.application.PolicyService;
import com.example.policies.messaging.events.ClaimEventPayload;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimEventsConsumer {

    private final PolicyService policyService;
    private final MeterRegistry meterRegistry;

    // -------------------------------------------------------------------------
    // Helper für Metriken
    // -------------------------------------------------------------------------

    private Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    private void stopSample(Timer.Sample sample, String outcome, String eventType) {
        sample.stop(
                Timer.builder("policies.kafka.consumer.latency")
                        .description("Kafka consumer latency for claim events in policy-service")
                        .tag("event_type", eventType)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementCounter(String outcome, String eventType) {
        Counter.builder("policies.kafka.consumer.events")
                .description("Number of claim events processed in policy-service")
                .tag("event_type", eventType)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    // -------------------------------------------------------------------------
    // Kafka-Listener
    // -------------------------------------------------------------------------

    @KafkaListener(
            topics = "claims.claim-events",
            groupId = "policy-service"
    )
    public void onClaimEvent(ClaimEventPayload event) {

        String eventType = event.getEventType() != null
                ? event.getEventType().name()
                : "UNKNOWN";

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            log.info("PolicyService received ClaimEvent: eventType={}, claimId={}, policyId={}",
                    eventType, event.getClaimId(), event.getPolicyId());

            // Fachliche Verarbeitung: Policy lookup & Logging
            if (event.getPolicyId() != null) {
                policyService.findById(event.getPolicyId())
                        .ifPresentOrElse(
                                policy -> log.info("Policy {} is {} for claim {}",
                                        policy.getPolicyNumber(), policy.getStatus(), event.getClaimId()),
                                () -> log.warn("No policy found for id {} (claimId={})",
                                        event.getPolicyId(), event.getClaimId())
                        );
            } else {
                log.warn("Received ClaimEvent without policyId (claimId={})", event.getClaimId());
            }

            outcome = "success";
            incrementCounter(outcome, eventType);

        } catch (Exception ex) {
            outcome = "error";
            incrementCounter(outcome, eventType);
            log.error("Error while handling ClaimEvent in PolicyService: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            stopSample(sample, outcome, eventType);
        }
    }
}
