package com.example.claims.infrastructure.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("rest")
public class RestPolicyClient implements PolicyClient {

    private final RestTemplate policyRestTemplate;

    @Value("${policy.service.base-url}")
    private String baseUrl;

    @Value("${spring.application.name:claims-service}")
    private String applicationName;

    @Override
    public Optional<PolicySummary> getPolicyById(UUID policyId) {
        String url = baseUrl + "/policies/" + policyId;

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Caller-Service", applicationName);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            log.info("RestPolicyClient.getPolicyById({}) called, url={}", policyId, url);

            ResponseEntity<PolicySummary> response =
                    policyRestTemplate.exchange(url, HttpMethod.GET, requestEntity, PolicySummary.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                PolicySummary summary = response.getBody();
                log.info("PolicyService returned policyNumber={} for policyId={}",
                        summary.policyNumber(), policyId);
                return Optional.of(summary);
            }

            log.warn("PolicyService returned status={} for policyId={}",
                    response.getStatusCode(), policyId);
            return Optional.empty();

        } catch (Exception ex) {
            log.error("Error calling PolicyService (REST) for policyId {}: {}", policyId, ex.getMessage(), ex);
            return Optional.empty();
        }
    }
}
