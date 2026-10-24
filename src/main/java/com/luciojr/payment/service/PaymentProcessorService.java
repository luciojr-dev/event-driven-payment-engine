package com.luciojr.payment.service;

import com.luciojr.payment.domain.Payment;
import com.luciojr.payment.domain.PaymentStatus;
import com.luciojr.payment.event.PaymentEvent;
import com.luciojr.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorService.class);
    private static final BigDecimal ANTIFRAUD_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal SIMULATED_TIMEOUT_AMOUNT = new BigDecimal("999.99");
    private static final BigDecimal SIMULATED_INSUFFICIENT_FUNDS_AMOUNT = new BigDecimal("404.00");

    private final PaymentRepository paymentRepository;

    public PaymentProcessorService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void processEvent(PaymentEvent event) {
        log.info("[Processor] Iniciando analise e liquidacao do pagamento {}: valor={}",
                event.paymentId(), event.amount());

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new IllegalStateException("Pagamento nao encontrado: " + event.paymentId()));

        // Cenário 1: Simulação de Timeout de Rede / Gateway Inoperante (Dispara Retry & DLQ)
        if (event.amount().compareTo(SIMULATED_TIMEOUT_AMOUNT) == 0) {
            log.error("[Processor] Gateway da adquirente retornou TIMEOUT na transacao {} (Simulando instabilidade)", event.paymentId());
            payment.setRetryCount(payment.getRetryCount() + 1);
            paymentRepository.save(payment);
            throw new GatewayTimeoutException("Timeout de comunicacao com a adquirente para o pagamento " + event.paymentId());
        }

        // Cenário 2: Motor Antifraude
        if (event.amount().compareTo(ANTIFRAUD_THRESHOLD) > 0) {
            log.warn("[Processor] Pagamento {} REJEITADO por risco de fraude (valor acima de R$ 5.000)", event.paymentId());
            payment.setStatus(PaymentStatus.REJECTED_ANTIFRAUD);
            payment.setStatusReason("Transação reprovada pela política de controle de riscos e antifraude.");
            paymentRepository.save(payment);
            return;
        }

        // Cenário 3: Saldo Insuficiente
        if (event.amount().compareTo(SIMULATED_INSUFFICIENT_FUNDS_AMOUNT) == 0
                || event.customerId().toLowerCase().contains("insufficient")) {
            log.warn("[Processor] Pagamento {} REJEITADO por saldo ou limite insuficiente", event.paymentId());
            payment.setStatus(PaymentStatus.REJECTED_INSUFFICIENT_FUNDS);
            payment.setStatusReason("Transação recusada pela instituição emissora: Limite/Saldo insuficiente.");
            paymentRepository.save(payment);
            return;
        }

        // Cenário 4: Aprovado com Sucesso
        log.info("[Processor] Pagamento {} APROVADO com sucesso!", event.paymentId());
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setStatusReason("Transação autorizada com sucesso pela adquirente.");
        paymentRepository.save(payment);
    }

    @Transactional
    public void markAsDlq(String paymentId, String reason) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED_SENT_TO_DLQ);
            payment.setStatusReason("Esgotadas tentativas de reprocessamento. Movido para Dead Letter Queue: " + reason);
            paymentRepository.save(payment);
        });
    }
}
