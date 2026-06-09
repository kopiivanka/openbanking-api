package com.kopytsia.openbanking.mock;

import com.kopytsia.openbanking.repository.Payment;
import com.kopytsia.openbanking.repository.PaymentRepository;
import com.kopytsia.openbanking.repository.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(value = "openbanking.seed.enabled", havingValue = "true", matchIfMissing = false)
public class PaymentSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentSeeder.class);

    private static final String DEBTOR = "DE89370400440532013000";
    private static final String CREDITOR = "DE02500105170137075030";

    private final PaymentRepository paymentRepository;

    public PaymentSeeder(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public void run(String... args) {
        long existing = paymentRepository.count();
        if (existing > 0) {
            log.info("payments table already has {} rows, skipping seed", existing);
            return;
        }

        Instant now = Instant.now();
        List<Payment> sample = List.of(
                build("120.00",   PaymentStatus.COMPLETED, "EXT-9001", null,
                        now.minus(3, ChronoUnit.DAYS)),
                build("45.50",    PaymentStatus.COMPLETED, "EXT-9002", null,
                        now.minus(2, ChronoUnit.DAYS)),
                build("10000.00", PaymentStatus.REJECTED,  null,       "Insufficient funds",
                        now.minus(1, ChronoUnit.DAYS)),
                build("75.00",    PaymentStatus.FAILED,    null,       "External bank timeout",
                        now.minus(6, ChronoUnit.HOURS)),
                build("30.00",    PaymentStatus.PENDING,   null,       null,
                        now.minus(15, ChronoUnit.MINUTES))
        );

        paymentRepository.saveAll(sample);
        log.info("seeded {} sample payments", sample.size());
    }

    private static Payment build(String amount, PaymentStatus status, String externalRef,
                                 String failureReason, Instant createdAt) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .debtorIban(DEBTOR)
                .creditorIban(CREDITOR)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .status(status)
                .externalReference(externalRef)
                .failureReason(failureReason)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
