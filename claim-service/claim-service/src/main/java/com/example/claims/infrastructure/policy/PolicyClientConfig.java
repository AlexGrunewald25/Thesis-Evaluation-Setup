package com.example.claims.infrastructure.policy;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PolicyClientConfig {

    @Bean
    public RestTemplate policyRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
