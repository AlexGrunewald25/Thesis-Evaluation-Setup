package com.example.policies.infrastructure.rest;

import com.example.policies.api.dto.PolicyResponse;
import com.example.policies.api.mapper.PolicyDtoMapper;
import com.example.policies.application.PolicyService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyRestController {

    private final PolicyService policyService;
    private final PolicyDtoMapper policyDtoMapper;
    private final MeterRegistry meterRegistry;

    @GetMapping("/{policyId}")
    public ResponseEntity<PolicyResponse> getById(@PathVariable UUID policyId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome;

        try {
            var policyOpt = policyService.findById(policyId);
            ResponseEntity<PolicyResponse> response;

            if (policyOpt.isPresent()) {
                outcome = "success";
                var responseBody = policyDtoMapper.toResponse(policyOpt.get());
                response = ResponseEntity.ok(responseBody);
            } else {
                outcome = "not_found";
                response = ResponseEntity.notFound().build();
            }

            return response;
        } finally {
            sample.stop(
                    meterRegistry.timer(
                            "policies.rest.latency",
                            "operation", "getById",
                            "outcome", "success" // oder outcome, wenn du ihn im finally sicher verfügbar hast
                    )
            );
        }
    }
}
