package com.example.policies.infrastructure.messaging;

import com.example.policies.domain.Policy;
import com.example.policies.messaging.events.PolicyEvaluationResultPayload;
import com.example.policies.messaging.events.PolicyEvaluationResultType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("event-driven")
public class PolicyEvaluationResultProducer {

    private final KafkaTemplate<String, PolicyEvaluationResultPayload> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${policies.events.evaluation-topic-name}")
    private String evaluationTopicName;

    public void publishPolicyEvaluationResult(UUID claimId, Policy policy, boolean coverageValid) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";

        try {
            PolicyEvaluationResultPayload payload = PolicyEvaluationResultPayload.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(coverageValid
                            ? PolicyEvaluationResultType.POLICY_EVALUATION_PASSED
                            : PolicyEvaluationResultType.POLICY_EVALUATION_FAILED)
                    .occurredAt(Instant.now())
                    .claimId(claimId)
                    .policyId(policy != null ? policy.getId() : null)
                    .policyNumber(policy != null ? policy.getPolicyNumber() : null)
                    .productCode(policy != null ? policy.getProductCode() : null)
                    .status(policy != null ? policy.getStatus().name() : null)
                    .validFrom(policy != null ? policy.getValidFrom() : null)
                    .validTo(policy != null ? policy.getValidTo() : null)
                    .coverageValid(coverageValid)
                    .build();

            String key = claimId != null ? claimId.toString()
                    : (policy != null && policy.getId() != null ? policy.getId().toString() : "unknown");

            CompletableFuture<SendResult<String, PolicyEvaluationResultPayload>> future =
                    kafkaTemplate.send(evaluationTopicName, key, payload);

            future.whenComplete((sendResult, ex) -> {
                if (ex != null) {
                    String errOutcome = "error";
                    incrementCounter(errOutcome);
                    stopSample(sample, errOutcome);
                    log.error("Failed to publish PolicyEvaluationResult for claimId={}", claimId, ex);
                } else {
                    incrementCounter("success");
                    stopSample(sample, "success");
                    if (log.isDebugEnabled() && sendResult != null) {
                        RecordMetadata metadata = sendResult.getRecordMetadata();
                        log.debug("Published PolicyEvaluationResult to topic={}, partition={}, offset={}",
                                metadata.topic(), metadata.partition(), metadata.offset());
                    }
                }
            });

        } catch (Exception ex) {
            outcome = "exception";
            incrementCounter(outcome);
            stopSample(sample, outcome);
            log.error("Unexpected error while publishing PolicyEvaluationResult for claimId={}", claimId, ex);
            throw ex;
        }
    }

    private void incrementCounter(String outcome) {
        Counter.builder("policies.events.published")
                .description("Number of policy evaluation result events published to Kafka")
                .tag("eventType", "POLICY_EVALUATION_RESULT")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void stopSample(Timer.Sample sample, String outcome) {
        sample.stop(
                Timer.builder("policies.events.publish.latency")
                        .description("Latency for publishing policy evaluation result events to Kafka")
                        .tag("eventType", "POLICY_EVALUATION_RESULT")
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }
}
