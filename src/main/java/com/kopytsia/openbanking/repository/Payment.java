package com.kopytsia.openbanking.repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    private UUID id;
    private String debtorIban;
    private String creditorIban;
    private BigDecimal amount;
    private String currency;
    @Setter private PaymentStatus status;
    @Setter private String externalReference;
    @Setter private String failureReason;
    private Instant createdAt;
    @Setter private Instant updatedAt;
}
