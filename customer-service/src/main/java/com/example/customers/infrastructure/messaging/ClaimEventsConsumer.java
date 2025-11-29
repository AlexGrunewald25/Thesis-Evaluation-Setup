package com.example.customers.infrastructure.messaging;

import com.example.customers.application.CustomerService;
import com.example.customers.domain.Customer;
import com.example.customers.messaging.events.ClaimEventPayload;
import com.example.customers.messaging.events.ClaimEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("event-driven")
public class ClaimEventsConsumer {

    private final CustomerService customerService;
    private final CustomerValidationResultProducer validationResultProducer;
    private final MeterRegistry meterRegistry;

    private Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    private void stopSample(Timer.Sample sample, String outcome, String eventType) {
        sample.stop(
                Timer.builder("customers.kafka.consumer.latency")
                        .description("Kafka consumer latency for claim events in customer-service")
                        .tag("event_type", eventType)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementCounter(String outcome, String eventType) {
        Counter.builder("customers.kafka.consumer.events")
                .description("Number of claim events processed in customer-service")
                .tag("event_type", eventType)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    @KafkaListener(
            topics = "claims.claim-events",
            groupId = "customer-service"
    )
    public void onClaimEvent(ClaimEventPayload event) {
        String eventTypeName = event.getEventType() != null
                ? event.getEventType().name()
                : "UNKNOWN";

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";

        try {
            log.info("CustomerService received ClaimEvent: eventType={}, claimId={}, customerId={}, customerNumber={}",
                    eventTypeName, event.getClaimId(), event.getCustomerId(), event.getCustomerNumber());

            if (event.getEventType() != ClaimEventType.CLAIM_SUBMITTED) {
                outcome = "ignored";
                incrementCounter(outcome, eventTypeName);
                return;
            }

            UUID customerId = event.getCustomerId();
            String customerNumber = event.getCustomerNumber();

            Optional<Customer> customerOpt;

            if (customerNumber != null && !customerNumber.isBlank()) {
                customerOpt = customerService.findByCustomerNumber(customerNumber);
            } else if (customerId != null) {
                customerOpt = customerService.findById(customerId);
            } else {
                customerOpt = Optional.empty();
            }

            Customer customer = customerOpt.orElse(null);

            validationResultProducer.publishValidationResult(
                    event.getClaimId(),
                    customerId,
                    customerNumber,
                    customer
            );

            outcome = "success";
            incrementCounter(outcome, eventTypeName);

        } catch (Exception ex) {
            outcome = "error";
            incrementCounter(outcome, eventTypeName);
            log.error("Error while handling ClaimEvent in CustomerService: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            stopSample(sample, outcome, eventTypeName);
        }
    }

}
