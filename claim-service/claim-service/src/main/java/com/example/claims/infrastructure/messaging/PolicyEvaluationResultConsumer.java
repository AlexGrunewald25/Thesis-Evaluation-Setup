package com.example.claims.infrastructure.messaging;

import com.example.claims.infrastructure.persistence.ClaimJpaRepository;
import com.example.claims.messaging.events.PolicyEvaluationResultPayload;
import com.example.claims.messaging.events.PolicyEvaluationResultType;
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
public class PolicyEvaluationResultConsumer {

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
                        .tag("source", "policy")
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
                .tag("source", "policy")
                .tag("event_type", eventType)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    // --------------------------- Listener -----------------------------------

    @KafkaListener(
            topics = "policies.policy-evaluation-events",
            groupId = "claims-service",
            containerFactory = "policyEvaluationKafkaListenerContainerFactory"
    )
    public void onPolicyEvaluationResult(PolicyEvaluationResultPayload event) {

        String eventTypeName = event.getEventType() != null
                ? event.getEventType().name()
                : "UNKNOWN";

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            UUID claimId = event.getClaimId();

            log.info(
                    "ClaimService received PolicyEvaluationResult: eventType={}, claimId={}, policyId={}, policyNumber={}, coverageValid={}",
                    eventTypeName,
                    claimId,
                    event.getPolicyId(),
                    event.getPolicyNumber(),
                    event.isCoverageValid()
            );

            if (claimId == null) {
                outcome = "no_claim_id";
                incrementCounter(outcome, eventTypeName);
                log.warn("PolicyEvaluationResult without claimId received, ignoring event");
                return;
            }

            boolean exists = claimJpaRepository.existsById(claimId);
            if (!exists) {
                outcome = "claim_not_found";
                incrementCounter(outcome, eventTypeName);
                log.warn("PolicyEvaluationResult for non-existing claimId={} received, ignoring", claimId);
                return;
            }

            // Hier könntest du später fachlich den Claim aktualisieren
            if (event.getEventType() == PolicyEvaluationResultType.POLICY_EVALUATION_PASSED) {
                log.info("Policy evaluation PASSED for claimId={}", claimId);
            } else {
                log.info("Policy evaluation FAILED for claimId={}", claimId);
            }

            outcome = "success";
            incrementCounter(outcome, eventTypeName);

        } catch (Exception ex) {
            outcome = "error";
            incrementCounter(outcome, eventTypeName);
            log.error("Error while handling PolicyEvaluationResult in ClaimService: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            stopSample(sample, outcome, eventTypeName);
        }
    }
}
