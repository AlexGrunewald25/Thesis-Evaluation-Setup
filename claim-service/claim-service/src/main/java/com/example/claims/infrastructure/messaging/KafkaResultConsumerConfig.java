package com.example.claims.infrastructure.messaging;

import com.example.claims.messaging.events.CustomerValidationResultPayload;
import com.example.claims.messaging.events.PolicyEvaluationResultPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@Profile("event-driven")
public class KafkaResultConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:claims-service}")
    private String groupId;

    private Map<String, Object> baseConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return props;
    }

    // -------------------------------------------------------------------------
    // CustomerValidationResult
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, CustomerValidationResultPayload> customerValidationConsumerFactory() {

        JsonDeserializer<CustomerValidationResultPayload> deserializer =
                new JsonDeserializer<>(CustomerValidationResultPayload.class);
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerConfigs(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerValidationResultPayload>
    customerValidationKafkaListenerContainerFactory(
            ConsumerFactory<String, CustomerValidationResultPayload> customerValidationConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, CustomerValidationResultPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(customerValidationConsumerFactory);
        factory.setBatchListener(false);
        return factory;
    }

    // -------------------------------------------------------------------------
    // PolicyEvaluationResult
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, PolicyEvaluationResultPayload> policyEvaluationConsumerFactory() {

        JsonDeserializer<PolicyEvaluationResultPayload> deserializer =
                new JsonDeserializer<>(PolicyEvaluationResultPayload.class);
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerConfigs(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PolicyEvaluationResultPayload>
    policyEvaluationKafkaListenerContainerFactory(
            ConsumerFactory<String, PolicyEvaluationResultPayload> policyEvaluationConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, PolicyEvaluationResultPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(policyEvaluationConsumerFactory);
        factory.setBatchListener(false);
        return factory;
    }
}
