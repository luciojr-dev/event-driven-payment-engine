package com.luciojr.payment.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciojr.payment.config.KafkaConfig;
import com.luciojr.payment.event.PaymentDlqEvent;
import com.luciojr.payment.event.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentEvent(PaymentEvent event) {
        log.info("[KafkaProducer] Publicando evento de pagamento no topico {}: paymentId={}, amount={}",
                KafkaConfig.PAYMENTS_TOPIC, event.paymentId(), event.amount());

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaConfig.PAYMENTS_TOPIC, event.paymentId(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[KafkaProducer] Falha ao enviar evento para o Kafka: {}", ex.getMessage());
                        } else {
                            log.info("[KafkaProducer] Evento enviado com sucesso! Offset: {}",
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("[KafkaProducer] Erro ao serializar evento: {}", e.getMessage());
        }
    }

    public void publishToDlq(PaymentDlqEvent dlqEvent) {
        log.warn("[KafkaProducer] Enviando mensagem para a Dead Letter Queue (DLQ) {}: paymentId={}, reason={}",
                KafkaConfig.PAYMENTS_DLQ_TOPIC, dlqEvent.paymentId(), dlqEvent.reason());

        try {
            String json = objectMapper.writeValueAsString(dlqEvent);
            kafkaTemplate.send(KafkaConfig.PAYMENTS_DLQ_TOPIC, dlqEvent.paymentId(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[KafkaProducer] Erro critico ao enviar para DLQ: {}", ex.getMessage());
                        } else {
                            log.info("[KafkaProducer] Mensagem persistida na DLQ com sucesso.");
                        }
                    });
        } catch (Exception e) {
            log.error("[KafkaProducer] Erro ao serializar evento DLQ: {}", e.getMessage());
        }
    }
}
