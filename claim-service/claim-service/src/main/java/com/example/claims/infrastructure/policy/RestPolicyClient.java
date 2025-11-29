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
@Profile("rest-sync")
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
        headers.add("X-Service-Name", applicationName);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<PolicySummary> response =
                    policyRestTemplate.exchange(url, HttpMethod.GET, requestEntity, PolicySummary.class);
            log.info("RestPolicyClient.getPolicyById({}) called", policyId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }

            log.warn("Policy {} not found, status={}", policyId, response.getStatusCode());
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Error calling PolicyService for id {}: {}", policyId, ex.getMessage());
            return Optional.empty();
        }
    }
}
