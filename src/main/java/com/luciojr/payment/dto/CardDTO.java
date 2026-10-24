package com.luciojr.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record CardDTO(
    @NotBlank(message = "O nome impresso no cartão é obrigatório")
    String holderName,

    @NotBlank(message = "O número do cartão é obrigatório")
    String cardNumber,

    @NotBlank(message = "A data de expiração é obrigatória")
    String expirationDate,

    @NotBlank(message = "O código CVV é obrigatório")
    String cvv
) {
    public String toMaskedNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        String clean = cardNumber.replaceAll("\\s+", "");
        String lastFour = clean.substring(clean.length() - 4);
        return "**** **** **** " + lastFour;
    }
}
