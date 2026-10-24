package com.luciojr.payment;

import com.luciojr.payment.domain.IdempotencyRecord;
import com.luciojr.payment.domain.Payment;
import com.luciojr.payment.domain.PaymentMethod;
import com.luciojr.payment.domain.PaymentStatus;
import com.luciojr.payment.dto.CardDTO;
import com.luciojr.payment.dto.PaymentRequestDTO;
import com.luciojr.payment.dto.PaymentResponseDTO;
import com.luciojr.payment.producer.PaymentKafkaProducer;
import com.luciojr.payment.repository.IdempotencyRepository;
import com.luciojr.payment.repository.PaymentRepository;
import com.luciojr.payment.service.GatewayTimeoutException;
import com.luciojr.payment.service.PaymentProcessorService;
import com.luciojr.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private PaymentKafkaProducer kafkaProducer;

    private PaymentService paymentService;
    private PaymentProcessorService processorService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, idempotencyRepository, kafkaProducer);
        processorService = new PaymentProcessorService(paymentRepository);
    }

    @Test
    @DisplayName("Deve enfileirar pagamento novo com sucesso e retornar status PROCESSING")
    void shouldEnqueueNewPaymentSuccessfully() {
        String idempotencyKey = "uuid-key-123";
        CardDTO card = new CardDTO("LUCIO JUNIOR", "4111111111111234", "10/30", "123");
        PaymentRequestDTO request = new PaymentRequestDTO(
                "cust_1", new BigDecimal("150.00"), "BRL", PaymentMethod.CREDIT_CARD, card);

        when(idempotencyRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponseDTO response = paymentService.processPayment(idempotencyKey, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.PROCESSING, response.status());
        assertEquals("**** **** **** 1234", response.maskedCard());
        verify(kafkaProducer, times(1)).publishPaymentEvent(any());
        verify(idempotencyRepository, times(1)).save(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Deve garantir idempotencia e retornar transacao original sem republicar no Kafka")
    void shouldReturnExistingPaymentWhenIdempotencyKeyRepeats() {
        String idempotencyKey = "uuid-key-duplicate";
        CardDTO card = new CardDTO("LUCIO JUNIOR", "4111111111111234", "10/30", "123");
        PaymentRequestDTO request = new PaymentRequestDTO(
                "cust_1", new BigDecimal("150.00"), "BRL", PaymentMethod.CREDIT_CARD, card);

        IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, "pay_existing_123", 202);
        Payment existingPayment = new Payment("pay_existing_123", idempotencyKey, "cust_1",
                new BigDecimal("150.00"), "BRL", PaymentMethod.CREDIT_CARD, "**** **** **** 1234");
        existingPayment.setStatus(PaymentStatus.APPROVED);

        when(idempotencyRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(record));
        when(paymentRepository.findById("pay_existing_123")).thenReturn(Optional.of(existingPayment));

        PaymentResponseDTO response = paymentService.processPayment(idempotencyKey, request);

        assertEquals("pay_existing_123", response.paymentId());
        assertEquals(PaymentStatus.APPROVED, response.status());
        assertTrue(response.message().contains("idempotente"));
        verify(kafkaProducer, never()).publishPaymentEvent(any());
    }

    @Test
    @DisplayName("Regra Antifraude: Valores acima de R$ 5.000 devem ser rejeitados")
    void shouldRejectWhenAmountExceedsAntifraudThreshold() {
        Payment payment = new Payment("pay_fraud_1", "key_fraud", "cust_high",
                new BigDecimal("6000.00"), "BRL", PaymentMethod.CREDIT_CARD, "**** 9999");

        when(paymentRepository.findById("pay_fraud_1")).thenReturn(Optional.of(payment));

        processorService.processEvent(com.luciojr.payment.event.PaymentEvent.create(
                "pay_fraud_1", "key_fraud", "cust_high", new BigDecimal("6000.00"),
                "BRL", PaymentMethod.CREDIT_CARD, "**** 9999"));

        assertEquals(PaymentStatus.REJECTED_ANTIFRAUD, payment.getStatus());
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    @DisplayName("Simulação de Timeout: Deve lançar GatewayTimeoutException para ativar Retry e DLQ")
    void shouldThrowTimeoutExceptionOnSpecificSimulatedAmount() {
        Payment payment = new Payment("pay_timeout_1", "key_timeout", "cust_timeout",
                new BigDecimal("999.99"), "BRL", PaymentMethod.CREDIT_CARD, "**** 1234");

        when(paymentRepository.findById("pay_timeout_1")).thenReturn(Optional.of(payment));

        assertThrows(GatewayTimeoutException.class, () -> {
            processorService.processEvent(com.luciojr.payment.event.PaymentEvent.create(
                    "pay_timeout_1", "key_timeout", "cust_timeout", new BigDecimal("999.99"),
                    "BRL", PaymentMethod.CREDIT_CARD, "**** 1234"));
        });

        assertEquals(1, payment.getRetryCount());
    }
}
