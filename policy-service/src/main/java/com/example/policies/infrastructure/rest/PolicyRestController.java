package com.example.policies.infrastructure.rest;

import com.example.policies.api.dto.PolicyResponse;
import com.example.policies.api.mapper.PolicyDtoMapper;
import com.example.policies.application.PolicyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
@Slf4j
public class PolicyRestController {

    private final PolicyService policyService;
    private final MeterRegistry meterRegistry;

    @GetMapping("/{id}")
    public ResponseEntity<PolicyResponseDto> getPolicyById(
            @PathVariable("id") UUID policyId,
            @RequestHeader(name = "X-Caller-Service", required = false) String callerService) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";

        try {
            log.info("Received policy lookup for policyId={} from callerService={}",
                    policyId, callerService != null ? callerService : "unknown");

            var policyOpt = policyService.findById(policyId);
            if (policyOpt.isEmpty()) {
                outcome = "not_found";
                incrementRestCounter("getPolicyById", outcome);
                return ResponseEntity.notFound().build();
            }

            var policy = policyOpt.get();
            var dto = PolicyResponseDto.fromDomain(policy);

            incrementRestCounter("getPolicyById", "success");
            return ResponseEntity.ok(dto);

        } catch (Exception ex) {
            outcome = "error";
            incrementRestCounter("getPolicyById", outcome);
            log.error("Error handling REST getPolicyById for policyId {}: {}", policyId, ex.getMessage(), ex);
            throw ex;
        } finally {
            stopRestTimer(sample, "getPolicyById", outcome);
        }
    }

    private void stopRestTimer(Timer.Sample sample, String method, String outcome) {
        sample.stop(
                Timer.builder("policies.rest.latency")
                        .description("REST latency per policy REST endpoint")
                        .tag("method", method)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementRestCounter(String method, String outcome) {
        Counter.builder("policies.rest.requests")
                .description("REST request count per policy REST endpoint")
                .tag("method", method)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
