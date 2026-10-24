package com.luciojr.payment.dto;

import com.luciojr.payment.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequestDTO(
    @NotBlank(message = "O ID do cliente (customer_id) é obrigatório")
    String customerId,

    @NotNull(message = "O valor do pagamento (amount) é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
    BigDecimal amount,

    String currency,

    @NotNull(message = "O método de pagamento (payment_method) é obrigatório")
    PaymentMethod paymentMethod,

    @Valid
    CardDTO card
) {
    public String getEffectiveCurrency() {
        return (currency == null || currency.isBlank()) ? "BRL" : currency.toUpperCase();
    }
}
