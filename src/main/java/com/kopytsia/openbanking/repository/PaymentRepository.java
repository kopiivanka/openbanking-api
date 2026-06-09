package com.kopytsia.openbanking.repository;

import com.kopytsia.openbanking.jooq.tables.records.PaymentsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.kopytsia.openbanking.jooq.tables.Payments.PAYMENTS;

@Repository
public class PaymentRepository {

    private final DSLContext dsl;

    public PaymentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long count() {
        return dsl.fetchCount(PAYMENTS);
    }

    public Payment save(Payment payment) {
        PaymentsRecord rec = toRecord(payment);
        rec.attach(dsl.configuration());
        rec.store();
        return toDomain(rec);
    }

    public void saveAll(List<Payment> payments) {
        List<PaymentsRecord> records = payments.stream()
                .map(PaymentRepository::toRecord)
                .toList();
        dsl.batchStore(records).execute();
    }

    public Payment update(Payment payment) {
        payment.setUpdatedAt(Instant.now());
        PaymentsRecord rec = toRecord(payment);
        rec.attach(dsl.configuration());
        rec.update();
        return toDomain(rec);
    }

    public Optional<Payment> findById(UUID id) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.ID.eq(id))
                .fetchOptional()
                .map(PaymentRepository::toDomain);
    }

    private static PaymentsRecord toRecord(Payment p) {
        PaymentsRecord rec = new PaymentsRecord();
        rec.setId(p.getId());
        rec.setDebtorIban(p.getDebtorIban());
        rec.setCreditorIban(p.getCreditorIban());
        rec.setAmount(p.getAmount());
        rec.setCurrency(p.getCurrency());
        rec.setStatus(p.getStatus().name());
        rec.setExternalReference(p.getExternalReference());
        rec.setFailureReason(p.getFailureReason());
        if (p.getCreatedAt() != null) rec.setCreatedAt(toOffsetDateTime(p.getCreatedAt()));
        if (p.getUpdatedAt() != null) rec.setUpdatedAt(toOffsetDateTime(p.getUpdatedAt()));
        return rec;
    }

    private static Payment toDomain(PaymentsRecord r) {
        return Payment.builder()
                .id(r.getId())
                .debtorIban(r.getDebtorIban())
                .creditorIban(r.getCreditorIban())
                .amount(r.getAmount())
                .currency(r.getCurrency())
                .status(PaymentStatus.valueOf(r.getStatus()))
                .externalReference(r.getExternalReference())
                .failureReason(r.getFailureReason())
                .createdAt(r.getCreatedAt().toInstant())
                .updatedAt(r.getUpdatedAt().toInstant())
                .build();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
