package com.kopytsia.openbanking.dto;

import com.kopytsia.openbanking.repository.Payment;
import com.kopytsia.openbanking.repository.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String debtorIban,
        String creditorIban,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String externalReference,
        String failureReason,
        Instant createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getDebtorIban(),
                p.getCreditorIban(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getExternalReference(),
                p.getFailureReason(),
                p.getCreatedAt()
        );
    }
}
