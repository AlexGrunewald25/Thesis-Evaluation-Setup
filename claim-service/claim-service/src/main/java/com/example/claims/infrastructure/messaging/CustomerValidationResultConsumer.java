package com.example.claims.infrastructure.messaging;

import com.example.claims.infrastructure.persistence.ClaimJpaRepository;
import com.example.claims.messaging.events.CustomerValidationResultPayload;
import com.example.claims.messaging.events.CustomerValidationResultType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("event-driven")
public class CustomerValidationResultConsumer {

    private final ClaimJpaRepository claimJpaRepository;
    private final MeterRegistry meterRegistry;

    // ------------------------- Metrics helpers ------------------------------

    private Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    private void stopSample(Timer.Sample sample, String outcome, String eventType) {
        sample.stop(
                Timer.builder("claims.kafka.consumer.latency")
                        .description("Kafka consumer latency in claims-service")
                        .tag("source", "customer")
                        .tag("event_type", eventType)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementCounter(String outcome, String eventType) {
        Counter.builder("claims.kafka.consumer.events")
                .description("Number of events processed in claims-service")
                .tag("source", "customer")
                .tag("event_type", eventType)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    // --------------------------- Listener -----------------------------------

    @KafkaListener(
            topics = "customers.customer-validation-events",
            groupId = "claims-service",
            containerFactory = "customerValidationKafkaListenerContainerFactory"
    )
    public void onCustomerValidationResult(CustomerValidationResultPayload event) {

        String eventTypeName = event.getEventType() != null
                ? event.getEventType().name()
                : "UNKNOWN";

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            UUID claimId = event.getClaimId();

            log.info(
                    "ClaimService received CustomerValidationResult: eventType={}, claimId={}, customerId={}, customerNumber={}, valid={}",
                    eventTypeName,
                    claimId,
                    event.getCustomerId(),
                    event.getCustomerNumber(),
                    event.isCustomerDataValid()
            );

            if (claimId == null) {
                outcome = "no_claim_id";
                incrementCounter(outcome, eventTypeName);
                log.warn("CustomerValidationResult without claimId received, ignoring event");
                return;
            }

            boolean exists = claimJpaRepository.existsById(claimId);
            if (!exists) {
                outcome = "claim_not_found";
                incrementCounter(outcome, eventTypeName);
                log.warn("CustomerValidationResult for non-existing claimId={} received, ignoring", claimId);
                return;
            }

            // Hier könntest du später fachlich den Claim aktualisieren
            // (Status, decisionReason etc.), für jetzt bleiben wir bei Logging.
            if (event.getEventType() == CustomerValidationResultType.CUSTOMER_VALIDATION_PASSED) {
                log.info("Customer validation PASSED for claimId={}", claimId);
            } else {
                log.info("Customer validation FAILED for claimId={}", claimId);
            }

            outcome = "success";
            incrementCounter(outcome, eventTypeName);

        } catch (Exception ex) {
            outcome = "error";
            incrementCounter(outcome, eventTypeName);
            log.error("Error while handling CustomerValidationResult in ClaimService: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            stopSample(sample, outcome, eventTypeName);
        }
    }
}
