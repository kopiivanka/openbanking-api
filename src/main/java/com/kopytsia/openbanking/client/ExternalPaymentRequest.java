package com.kopytsia.openbanking.client;

import java.math.BigDecimal;
import java.util.UUID;

public record ExternalPaymentRequest(
        UUID localPaymentId,
        String debtorIban,
        String creditorIban,
        BigDecimal amount,
        String currency
) {
}
