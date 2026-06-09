package com.kopytsia.openbanking.service;

import com.kopytsia.openbanking.dto.BalanceResponse;
import com.kopytsia.openbanking.dto.PaymentRequest;
import com.kopytsia.openbanking.client.ExternalBankClient;
import com.kopytsia.openbanking.exception.CurrencyMismatchException;
import com.kopytsia.openbanking.exception.ExternalBankException;
import com.kopytsia.openbanking.exception.InsufficientFundsException;
import com.kopytsia.openbanking.client.ExternalPaymentRequest;
import com.kopytsia.openbanking.client.ExternalPaymentResponse;
import com.kopytsia.openbanking.repository.Payment;
import com.kopytsia.openbanking.repository.PaymentRepository;
import com.kopytsia.openbanking.repository.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private record SavedSnapshot(String op, PaymentStatus status, String externalReference, String failureReason) {}
    private final List<SavedSnapshot> savedSnapshots = new ArrayList<>();

    private static final String DEBTOR = "DE89370400440532013000";
    private static final String CREDITOR = "DE02500105170137075030";

    @Mock private PaymentRepository paymentRepository;
    @Mock private ExternalBankClient bankClient;

    @InjectMocks private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        savedSnapshots.clear();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            savedSnapshots.add(new SavedSnapshot("insert", p.getStatus(),
                    p.getExternalReference(), p.getFailureReason()));
            return p;
        });
        when(paymentRepository.update(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            savedSnapshots.add(new SavedSnapshot("update", p.getStatus(),
                    p.getExternalReference(), p.getFailureReason()));
            return p;
        });
    }

    @Test
    void initiate_completes_payment_when_funds_sufficient() {
        when(bankClient.fetchBalance(DEBTOR))
                .thenReturn(new BalanceResponse(DEBTOR, new BigDecimal("1000.00"), "EUR"));
        when(bankClient.submitPayment(any(ExternalPaymentRequest.class)))
                .thenReturn(new ExternalPaymentResponse("EXT-123", "ACCEPTED"));

        Payment result = service.initiate(new PaymentRequest(DEBTOR, CREDITOR, new BigDecimal("100.00"), "EUR"));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getExternalReference()).isEqualTo("EXT-123");

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(paymentRepository, times(1)).update(any(Payment.class));
        assertThat(savedSnapshots).extracting(SavedSnapshot::status)
                .containsExactly(PaymentStatus.PENDING, PaymentStatus.COMPLETED);
    }

    @Test
    void initiate_rejects_when_funds_insufficient_and_persists_rejection() {
        when(bankClient.fetchBalance(DEBTOR))
                .thenReturn(new BalanceResponse(DEBTOR, new BigDecimal("10.00"), "EUR"));

        assertThatThrownBy(() ->
                service.initiate(new PaymentRequest(DEBTOR, CREDITOR, new BigDecimal("100.00"), "EUR")))
                .isInstanceOf(InsufficientFundsException.class);

        verify(bankClient, never()).submitPayment(any());
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentRepository, never()).update(any(Payment.class));
        assertThat(savedSnapshots).singleElement()
                .satisfies(s -> {
                    assertThat(s.status()).isEqualTo(PaymentStatus.REJECTED);
                    assertThat(s.failureReason()).isEqualTo("Insufficient funds");
                });
    }

    @Test
    void initiate_rejects_on_currency_mismatch() {
        when(bankClient.fetchBalance(DEBTOR))
                .thenReturn(new BalanceResponse(DEBTOR, new BigDecimal("1000.00"), "EUR"));

        assertThatThrownBy(() ->
                service.initiate(new PaymentRequest(DEBTOR, CREDITOR, new BigDecimal("10.00"), "USD")))
                .isInstanceOf(CurrencyMismatchException.class);

        verify(bankClient, never()).submitPayment(any());
        verify(paymentRepository, never()).save(any());
        verify(paymentRepository, never()).update(any());
    }

    @Test
    void initiate_marks_failed_when_external_call_fails() {
        when(bankClient.fetchBalance(DEBTOR))
                .thenReturn(new BalanceResponse(DEBTOR, new BigDecimal("1000.00"), "EUR"));
        when(bankClient.submitPayment(any()))
                .thenThrow(new ExternalBankException("bank down"));

        assertThatThrownBy(() ->
                service.initiate(new PaymentRequest(DEBTOR, CREDITOR, new BigDecimal("100.00"), "EUR")))
                .isInstanceOf(ExternalBankException.class);

        verify(paymentRepository).save(any(Payment.class));
        verify(paymentRepository).update(any(Payment.class));
        assertThat(savedSnapshots).hasSize(2);
        assertThat(savedSnapshots.get(0).status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedSnapshots.get(1).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(savedSnapshots.get(1).failureReason()).contains("bank down");
    }
}
