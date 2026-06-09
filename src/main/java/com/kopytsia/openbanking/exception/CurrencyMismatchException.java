package com.kopytsia.openbanking.exception;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String accountCurrency, String paymentCurrency) {
        super("Payment currency " + paymentCurrency + " does not match account currency " + accountCurrency);
    }
}
