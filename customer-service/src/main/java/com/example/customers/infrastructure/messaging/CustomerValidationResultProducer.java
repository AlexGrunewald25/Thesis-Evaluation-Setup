package com.example.customers.infrastructure.messaging;

import com.example.customers.domain.Customer;
import com.example.customers.messaging.events.CustomerValidationResultPayload;
import com.example.customers.messaging.events.CustomerValidationResultType;
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
public class CustomerValidationResultProducer {

    private final KafkaTemplate<String, CustomerValidationResultPayload> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${customers.events.validation-topic-name}")
    private String validationTopicName;

    public void publishValidationResult(UUID claimId,
                                        UUID customerId,
                                        String customerNumber,
                                        Customer customer) {

        boolean valid = customer != null && customer.isCustomerDataValid();

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";

        try {
            CustomerValidationResultPayload payload = CustomerValidationResultPayload.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(valid
                            ? CustomerValidationResultType.CUSTOMER_VALIDATION_PASSED
                            : CustomerValidationResultType.CUSTOMER_VALIDATION_FAILED)
                    .occurredAt(Instant.now())
                    .claimId(claimId)
                    .customerId(customerId)
                    .customerNumber(customerNumber)
                    .addressComplete(customer != null && customer.isAddressComplete())
                    .contactDataComplete(customer != null && customer.isContactDataComplete())
                    .customerDataValid(valid)
                    .build();

            String key = claimId != null ? claimId.toString()
                    : (customerId != null ? customerId.toString() : customerNumber);

            CompletableFuture<SendResult<String, CustomerValidationResultPayload>> future =
                    kafkaTemplate.send(validationTopicName, key, payload);

            future.whenComplete((sendResult, ex) -> {
                if (ex != null) {
                    String errOutcome = "error";
                    incrementCounter(errOutcome);
                    stopSample(sample, errOutcome);
                    log.error("Failed to publish CustomerValidationResult for claimId={} customerId={} customerNumber={}",
                            claimId, customerId, customerNumber, ex);
                } else {
                    incrementCounter("success");
                    stopSample(sample, "success");
                    if (log.isDebugEnabled() && sendResult != null) {
                        RecordMetadata metadata = sendResult.getRecordMetadata();
                        log.debug("Published CustomerValidationResult to topic={}, partition={}, offset={}",
                                metadata.topic(), metadata.partition(), metadata.offset());
                    }
                }
            });

        } catch (Exception ex) {
            outcome = "exception";
            incrementCounter(outcome);
            stopSample(sample, outcome);
            log.error("Unexpected error while publishing CustomerValidationResult for claimId={} customerId={}",
                    claimId, customerId, ex);
            throw ex;
        }
    }

    private void incrementCounter(String outcome) {
        Counter.builder("customers.events.published")
                .description("Number of customer validation result events published to Kafka")
                .tag("eventType", "CUSTOMER_VALIDATION_RESULT")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void stopSample(Timer.Sample sample, String outcome) {
        sample.stop(
                Timer.builder("customers.events.publish.latency")
                        .description("Latency for publishing customer validation result events to Kafka")
                        .tag("eventType", "CUSTOMER_VALIDATION_RESULT")
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }
}
