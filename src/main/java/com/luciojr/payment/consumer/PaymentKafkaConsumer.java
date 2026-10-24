package com.luciojr.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luciojr.payment.config.KafkaConfig;
import com.luciojr.payment.domain.DlqRecord;
import com.luciojr.payment.event.PaymentDlqEvent;
import com.luciojr.payment.event.PaymentEvent;
import com.luciojr.payment.producer.PaymentKafkaProducer;
import com.luciojr.payment.repository.DlqRecordRepository;
import com.luciojr.payment.service.GatewayTimeoutException;
import com.luciojr.payment.service.PaymentProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentKafkaConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final PaymentProcessorService processorService;
    private final PaymentKafkaProducer producer;
    private final DlqRecordRepository dlqRecordRepository;
    private final ObjectMapper objectMapper;

    // Rastreia tentativas em memoria por paymentId para simulacao resiliente
    private final Map<String, Integer> retryTracker = new ConcurrentHashMap<>();

    public PaymentKafkaConsumer(PaymentProcessorService processorService,
                                PaymentKafkaProducer producer,
                                DlqRecordRepository dlqRecordRepository,
                                ObjectMapper objectMapper) {
        this.processorService = processorService;
        this.producer = producer;
        this.dlqRecordRepository = dlqRecordRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaConfig.PAYMENTS_TOPIC, groupId = "payment-engine-group")
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("[KafkaConsumer] Recebido evento de pagamento: paymentId={}, amount={}",
                event.paymentId(), event.amount());

        int currentAttempt = retryTracker.getOrDefault(event.paymentId(), 0);

        try {
            processorService.processEvent(event);
            // Se processou com sucesso, limpa contador
            retryTracker.remove(event.paymentId());
        } catch (GatewayTimeoutException ex) {
            currentAttempt++;
            retryTracker.put(event.paymentId(), currentAttempt);

            log.warn("[KafkaConsumer] Falha transiente na tentativa {}/{} para o pagamento {}: {}",
                    currentAttempt, MAX_RETRIES, event.paymentId(), ex.getMessage());

            if (currentAttempt >= MAX_RETRIES) {
                handleExhaustedRetries(event, ex.getMessage(), currentAttempt);
                retryTracker.remove(event.paymentId());
            } else {
                // Simula backoff exponencial simples antes da proxima tentativa
                try {
                    long backoffMs = (long) Math.pow(2, currentAttempt) * 500;
                    log.info("[KafkaConsumer] Aguardando backoff de {}ms antes de retentar...", backoffMs);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                // Retenta reprocessar o mesmo evento
                consumePaymentEvent(event);
            }
        } catch (Exception ex) {
            log.error("[KafkaConsumer] Erro nao recuperavel no pagamento {}: {}", event.paymentId(), ex.getMessage());
            handleExhaustedRetries(event, ex.getMessage(), currentAttempt);
            retryTracker.remove(event.paymentId());
        }
    }

    @KafkaListener(topics = KafkaConfig.PAYMENTS_DLQ_TOPIC, groupId = "payment-dlq-monitor-group")
    public void consumeDlqEvent(PaymentDlqEvent dlqEvent) {
        log.error("[DLQ-MONITOR] ALERTA: Mensagem na Dead Letter Queue! Payment ID: {}, Motivo: {}",
                dlqEvent.paymentId(), dlqEvent.reason());
    }

    private void handleExhaustedRetries(PaymentEvent event, String reason, int attempts) {
        log.error("[DLQ] Limite de tentativas ({}) esgotado para o pagamento {}. Roteando para Dead Letter Queue.",
                attempts, event.paymentId());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            payloadJson = event.toString();
        }

        // 1. Atualiza status no banco para FAILED_SENT_TO_DLQ
        processorService.markAsDlq(event.paymentId(), reason);

        // 2. Persiste registro de auditoria da DLQ
        DlqRecord dlqRecord = new DlqRecord(event.paymentId(), reason, payloadJson, attempts);
        dlqRecordRepository.save(dlqRecord);

        // 3. Publica evento no topico payments.dlq
        PaymentDlqEvent dlqEvent = new PaymentDlqEvent(
                event.paymentId(),
                event.idempotencyKey(),
                reason,
                attempts,
                event,
                Instant.now()
        );
        producer.publishToDlq(dlqEvent);
    }
}
