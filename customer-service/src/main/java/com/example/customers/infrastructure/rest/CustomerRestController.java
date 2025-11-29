package com.example.customers.infrastructure.rest;

import com.example.customers.application.CustomerService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Slf4j
@Profile("rest")
public class CustomerRestController {

    private final CustomerService customerService;
    private final MeterRegistry meterRegistry;

    @GetMapping("/{customerId}/valid")
    public ResponseEntity<Boolean> isCustomerDataValid(
            @PathVariable("customerId") UUID customerId,
            @RequestHeader(name = "X-Caller-Service", required = false) String callerService) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";

        try {
            log.info(
                    "REST isCustomerDataValid called for customerId={} from callerService={}",
                    customerId,
                    callerService != null ? callerService : "unknown"
            );

            boolean valid = customerService.isCustomerDataValidById(customerId);

            log.info(
                    "REST isCustomerDataValid result for customerId={}: valid={}",
                    customerId,
                    valid
            );

            incrementRestCounter("isCustomerDataValid", "success");
            return ResponseEntity.ok(valid);

        } catch (Exception ex) {
            outcome = "error";
            incrementRestCounter("isCustomerDataValid", outcome);
            log.error(
                    "Error handling REST isCustomerDataValid for customerId {}: {}",
                    customerId,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        } finally {
            stopRestTimer(sample, "isCustomerDataValid", outcome);
        }
    }

    // -------------------------------------------------------------------------
    // Metrics helpers
    // -------------------------------------------------------------------------

    private void stopRestTimer(Timer.Sample sample, String method, String outcome) {
        sample.stop(
                Timer.builder("customers.rest.latency")
                        .description("REST latency per customer REST endpoint")
                        .tag("method", method)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementRestCounter(String method, String outcome) {
        meterRegistry.counter(
                        "customers.rest.requests",
                        "method", method,
                        "outcome", outcome
                )
                .increment();
    }
}
