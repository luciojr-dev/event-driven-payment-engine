package com.luciojr.payment.service;

import com.luciojr.payment.domain.IdempotencyRecord;
import com.luciojr.payment.domain.Payment;
import com.luciojr.payment.dto.PaymentRequestDTO;
import com.luciojr.payment.dto.PaymentResponseDTO;
import com.luciojr.payment.event.PaymentEvent;
import com.luciojr.payment.producer.PaymentKafkaProducer;
import com.luciojr.payment.repository.IdempotencyRepository;
import com.luciojr.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final PaymentKafkaProducer kafkaProducer;

    public PaymentService(PaymentRepository paymentRepository,
                          IdempotencyRepository idempotencyRepository,
                          PaymentKafkaProducer kafkaProducer) {
        this.paymentRepository = paymentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    public PaymentResponseDTO processPayment(String idempotencyKey, PaymentRequestDTO request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("O header 'Idempotency-Key' é obrigatório para evitar duplicações.");
        }

        // 1. Verificacao de Idempotencia
        Optional<IdempotencyRecord> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingKey.isPresent()) {
            log.warn("[Idempotency] Chave repetida detectada: {}. Retornando transacao original.", idempotencyKey);
            Payment existingPayment = paymentRepository.findById(existingKey.get().getPaymentId())
                    .orElseThrow(() -> new IllegalStateException("Registro de pagamento associado não encontrado"));

            return PaymentResponseDTO.fromEntity(existingPayment,
                    "Transação idempotente: Requisição idêntica já processada/em andamento. Nenhuma nova cobrança efetuada.");
        }

        // 2. Criacao do Pagamento com mascara de seguranca
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String maskedCard = (request.card() != null) ? request.card().toMaskedNumber() : "N/A";

        Payment payment = new Payment(
                paymentId,
                idempotencyKey,
                request.customerId(),
                request.amount(),
                request.getEffectiveCurrency(),
                request.paymentMethod(),
                maskedCard
        );

        paymentRepository.save(payment);

        // 3. Gravacao da chave de idempotencia
        IdempotencyRecord idempotencyRecord = new IdempotencyRecord(idempotencyKey, paymentId, 202);
        idempotencyRepository.save(idempotencyRecord);

        // 4. Publicacao Assincrona no Kafka
        PaymentEvent event = PaymentEvent.create(
                paymentId,
                idempotencyKey,
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                maskedCard
        );

        kafkaProducer.publishPaymentEvent(event);

        return PaymentResponseDTO.fromEntity(payment,
                "Pagamento aceito e enfileirado para processamento assíncrono via Kafka.");
    }

    public Optional<PaymentResponseDTO> getPaymentById(String paymentId) {
        return paymentRepository.findById(paymentId)
                .map(p -> PaymentResponseDTO.fromEntity(p, "Status da transação recuperado."));
    }

    public List<Payment> getRecentPayments() {
        return paymentRepository.findAll();
    }
}
