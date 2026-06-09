package com.kopytsia.openbanking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDto(
        String id,
        Instant bookingDate,
        BigDecimal amount,
        String currency,
        String counterpartyIban,
        String description
) {
}
