package com.luciojr.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String PAYMENTS_TOPIC = "payments.v1";
    public static final String PAYMENTS_DLQ_TOPIC = "payments.dlq";

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name(PAYMENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentsDlqTopic() {
        return TopicBuilder.name(PAYMENTS_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
