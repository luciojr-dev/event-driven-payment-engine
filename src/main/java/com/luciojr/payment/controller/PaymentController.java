package com.luciojr.payment.controller;

import com.luciojr.payment.domain.DlqRecord;
import com.luciojr.payment.domain.Payment;
import com.luciojr.payment.dto.PaymentRequestDTO;
import com.luciojr.payment.dto.PaymentResponseDTO;
import com.luciojr.payment.repository.DlqRecordRepository;
import com.luciojr.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments API", description = "Endpoints para emissão assíncrona, consulta de status e auditoria de DLQ")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;
    private final DlqRecordRepository dlqRecordRepository;

    public PaymentController(PaymentService paymentService, DlqRecordRepository dlqRecordRepository) {
        this.paymentService = paymentService;
        this.dlqRecordRepository = dlqRecordRepository;
    }

    @PostMapping
    @Operation(summary = "Enviar solicitação de pagamento assíncrono",
               description = "Valida a idempotência, enfileira o evento no Apache Kafka e retorna HTTP 202 imediatamente.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Pagamento recebido e enfileirado para processamento"),
        @ApiResponse(responseCode = "400", description = "Payload inválido ou cabeçalho Idempotency-Key ausente")
    })
    public ResponseEntity<PaymentResponseDTO> submitPayment(
            @Parameter(description = "Chave única de idempotência da transação (UUID)", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequestDTO request) {

        PaymentResponseDTO response = paymentService.processPayment(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Consultar status atual de um pagamento por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalhes e status da transação recuperados"),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado")
    })
    public ResponseEntity<PaymentResponseDTO> getPaymentStatus(@PathVariable String paymentId) {
        return paymentService.getPaymentById(paymentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Listar todas as transações de pagamento")
    public ResponseEntity<List<Payment>> listAllPayments() {
        return ResponseEntity.ok(paymentService.getRecentPayments());
    }

    @GetMapping("/dlq")
    @Operation(summary = "Listar transações enviadas para a Dead Letter Queue (DLQ)")
    public ResponseEntity<List<DlqRecord>> listDlqRecords() {
        return ResponseEntity.ok(dlqRecordRepository.findAllByOrderByCreatedAtDesc());
    }
}
