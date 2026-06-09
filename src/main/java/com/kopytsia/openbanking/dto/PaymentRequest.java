package com.kopytsia.openbanking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$",
                 message = "must be a valid IBAN")
        String debtorIban,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$",
                 message = "must be a valid IBAN")
        String creditorIban,

        @NotNull
        @DecimalMin(value = "0.01", message = "must be greater than 0")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be ISO 4217 currency code")
        String currency
) {
}
