package com.example.policies.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("event-driven")
public class PolicyMessagingConfig {

    @Bean
    public NewTopic policyEvaluationResultTopic(
            @Value("${policies.events.evaluation-topic-name}") String topicName) {

        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
