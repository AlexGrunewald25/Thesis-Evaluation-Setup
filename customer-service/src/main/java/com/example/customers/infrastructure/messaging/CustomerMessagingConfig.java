package com.example.customers.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class CustomerMessagingConfig {

    @Bean
    public NewTopic customerValidationResultTopic(
            @Value("${customers.events.validation-topic-name}") String topicName) {

        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
