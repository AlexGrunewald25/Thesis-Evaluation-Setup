package com.example.claims.infrastructure.customer;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CustomerClientConfig {

    @Bean
    @Profile("rest")
    public RestTemplate customerRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
