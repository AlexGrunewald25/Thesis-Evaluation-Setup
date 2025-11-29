package com.example.claims.infrastructure.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("rest")
public class RestCustomerClient implements CustomerClient {

    private final RestTemplate customerRestTemplate;

    @Value("${customer.service.base-url}")
    private String baseUrl;

    @Value("${spring.application.name:claims-service}")
    private String applicationName;

    @Override
    public boolean isCustomerDataValid(UUID customerId) {
        String url = baseUrl + "/customers/" + customerId + "/valid";

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Caller-Service", applicationName);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            log.info("RestCustomerClient.isCustomerDataValid({}) called, url={}", customerId, url);

            ResponseEntity<Boolean> response =
                    customerRestTemplate.exchange(url, HttpMethod.GET, requestEntity, Boolean.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Boolean body = response.getBody();
                boolean valid = Boolean.TRUE.equals(body);
                log.info("Customer {} validation result via REST: {}", customerId, valid);
                return valid;
            }

            log.warn("Customer {} validation via REST returned non-2xx status={}",
                    customerId, response.getStatusCode());
            return false;
        } catch (Exception ex) {
            log.error("Error calling CustomerService (REST) for id {}: {}", customerId, ex.getMessage(), ex);
            return false;
        }
    }
}
