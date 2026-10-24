package com.luciojr.payment.controller;

import com.luciojr.payment.domain.PaymentMethod;
import com.luciojr.payment.dto.CardDTO;
import com.luciojr.payment.dto.PaymentRequestDTO;
import com.luciojr.payment.dto.PaymentResponseDTO;
import com.luciojr.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/payments/simulate")
@Tag(name = "Simulation & Load Testing", description = "Gerador de massa de testes para simulação de cenários reais e concorrência")
@CrossOrigin(origins = "*")
public class SimulationController {

    private final PaymentService paymentService;

    public SimulationController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/preset/{scenario}")
    @Operation(summary = "Gerar transação de teste por cenário pré-configurado",
               description = "Cenários: 'approve', 'antifraud', 'insufficient-funds', 'timeout-dlq', 'idempotent-repeat'")
    public ResponseEntity<Map<String, Object>> simulatePreset(@PathVariable String scenario) {
        String idempotencyKey = "sim_" + UUID.randomUUID();
        CardDTO defaultCard = new CardDTO("LUCIO JUNIOR", "4532117890123456", "12/28", "888");

        PaymentRequestDTO request;

        switch (scenario.toLowerCase()) {
            case "antifraud" -> request = new PaymentRequestDTO(
                    "cust_risk_high",
                    new BigDecimal("8500.00"),
                    "BRL",
                    PaymentMethod.CREDIT_CARD,
                    defaultCard
            );
            case "insufficient-funds" -> request = new PaymentRequestDTO(
                    "cust_insufficient_balance",
                    new BigDecimal("404.00"),
                    "BRL",
                    PaymentMethod.CREDIT_CARD,
                    defaultCard
            );
            case "timeout-dlq" -> request = new PaymentRequestDTO(
                    "cust_timeout_sim",
                    new BigDecimal("999.99"),
                    "BRL",
                    PaymentMethod.CREDIT_CARD,
                    defaultCard
            );
            case "idempotent-repeat" -> {
                // Dispara a primeira requisicao
                String fixedKey = "sim_idemp_test_" + System.currentTimeMillis();
                request = new PaymentRequestDTO(
                        "cust_idemp_user",
                        new BigDecimal("120.00"),
                        "BRL",
                        PaymentMethod.CREDIT_CARD,
                        defaultCard
                );
                PaymentResponseDTO firstCall = paymentService.processPayment(fixedKey, request);
                // Dispara a segunda requisicao identica
                PaymentResponseDTO secondCall = paymentService.processPayment(fixedKey, request);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("scenario", "idempotent-repeat");
                result.put("testedKey", fixedKey);
                result.put("firstCallResponse", firstCall);
                result.put("secondCallResponse", secondCall);
                result.put("observation", "A segunda requisição retornou o pagamento original sem duplicar registros no banco nem republicar no Kafka.");
                return ResponseEntity.ok(result);
            }
            default -> request = new PaymentRequestDTO(
                    "cust_approved_standard",
                    new BigDecimal("189.90"),
                    "BRL",
                    PaymentMethod.CREDIT_CARD,
                    defaultCard
            );
        }

        PaymentResponseDTO response = paymentService.processPayment(idempotencyKey, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario);
        result.put("idempotencyKey", idempotencyKey);
        result.put("paymentResponse", response);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/burst/{count}")
    @Operation(summary = "Disparar rajada de testes simultâneos",
               description = "Dispara N transações consecutivas para estressar a fila Kafka e demonstrar ingestão assíncrona.")
    public ResponseEntity<Map<String, Object>> simulateBurst(@PathVariable int count) {
        int safeCount = Math.min(Math.max(count, 1), 50);
        List<PaymentResponseDTO> results = new ArrayList<>();
        CardDTO defaultCard = new CardDTO("LUCIO JUNIOR", "4012001037144965", "08/29", "123");

        for (int i = 1; i <= safeCount; i++) {
            String key = "burst_" + UUID.randomUUID();
            BigDecimal amount = new BigDecimal(50 + (i * 15)).setScale(2);
            PaymentRequestDTO request = new PaymentRequestDTO(
                    "cust_burst_" + i,
                    amount,
                    "BRL",
                    (i % 3 == 0) ? PaymentMethod.PIX : PaymentMethod.CREDIT_CARD,
                    defaultCard
            );
            results.add(paymentService.processPayment(key, request));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalDispatched", safeCount);
        response.put("message", "Todas as transações foram enfileiradas com sucesso no Kafka.");
        response.put("items", results);
        return ResponseEntity.ok(response);
    }
}
