package com.kopytsia.openbanking.service;

import com.kopytsia.openbanking.client.ExternalBankClient;
import com.kopytsia.openbanking.client.ExternalPaymentRequest;
import com.kopytsia.openbanking.dto.PaymentRequest;
import com.kopytsia.openbanking.exception.CurrencyMismatchException;
import com.kopytsia.openbanking.exception.ExternalBankException;
import com.kopytsia.openbanking.exception.InsufficientFundsException;
import com.kopytsia.openbanking.repository.Payment;
import com.kopytsia.openbanking.repository.PaymentRepository;
import com.kopytsia.openbanking.repository.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final ExternalBankClient bankClient;

    public PaymentServiceImpl(PaymentRepository paymentRepository, ExternalBankClient bankClient) {
        this.paymentRepository = paymentRepository;
        this.bankClient = bankClient;
    }

    @Override
    @Transactional
    public Payment initiate(PaymentRequest request) {
        var balance = bankClient.fetchBalance(request.debtorIban());

        if (!balance.currency().equalsIgnoreCase(request.currency())) {
            throw new CurrencyMismatchException(balance.currency(), request.currency());
        }
        if (balance.amount().compareTo(request.amount()) < 0) {
            var rejected = paymentRepository.save(buildPayment(request, PaymentStatus.REJECTED, null, "Insufficient funds"));
            log.info("Payment {} rejected: insufficient funds", rejected.getId());
            throw new InsufficientFundsException(request.debtorIban());
        }

        var pending = paymentRepository.save(buildPayment(request, PaymentStatus.PENDING, null, null));
        log.info("Payment {} persisted as PENDING, submitting to external bank", pending.getId());

        try {
            var extResponse = bankClient.submitPayment(new ExternalPaymentRequest(
                    pending.getId(),
                    pending.getDebtorIban(),
                    pending.getCreditorIban(),
                    pending.getAmount(),
                    pending.getCurrency()
            ));
            pending.setStatus(PaymentStatus.COMPLETED);
            pending.setExternalReference(extResponse.externalReference());
            return paymentRepository.update(pending);
        } catch (ExternalBankException e) {
            log.warn("Payment {} failed at external bank: {}", pending.getId(), e.getMessage());
            pending.setStatus(PaymentStatus.FAILED);
            pending.setFailureReason(e.getMessage());
            paymentRepository.update(pending);
            throw e;
        }
    }

    private Payment buildPayment(PaymentRequest request, PaymentStatus status,
                                 String externalReference, String failureReason) {
        Instant now = Instant.now();
        return Payment.builder()
                .id(UUID.randomUUID())
                .debtorIban(request.debtorIban())
                .creditorIban(request.creditorIban())
                .amount(request.amount())
                .currency(request.currency())
                .status(status)
                .externalReference(externalReference)
                .failureReason(failureReason)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
