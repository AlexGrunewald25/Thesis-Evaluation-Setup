package com.example.policies.infrastructure.messaging;

import com.example.policies.messaging.events.ClaimEventPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:policy-service}")
    private String groupId;

    @Bean
    public Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // WICHTIG: KEINE Konfiguration des Value-Deserializers über Properties,
        // damit JacksonJsonDeserializer nur über Setter/Constructor konfiguriert wird.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return props;
    }

    @Bean
    public ConsumerFactory<String, ClaimEventPayload> consumerFactory() {
        // JacksonJsonDeserializer nur hier konfigurieren
        JacksonJsonDeserializer<ClaimEventPayload> deserializer =
                new JacksonJsonDeserializer<>(ClaimEventPayload.class);
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs(),
                new StringDeserializer(),
                deserializer
        );
    }

    /**
     * Standard-Listener-Container-Factory, die von @KafkaListener ohne
     * weiteren Namen verwendet wird.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClaimEventPayload> kafkaListenerContainerFactory(
            ConsumerFactory<String, ClaimEventPayload> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, ClaimEventPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(false);
        return factory;
    }
}
