package com.kopytsia.openbanking.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String iban,
        BigDecimal amount,
        String currency
) {
}
