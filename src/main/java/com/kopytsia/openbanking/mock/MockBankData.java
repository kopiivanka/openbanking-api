package com.kopytsia.openbanking.mock;

import com.kopytsia.openbanking.dto.BalanceResponse;
import com.kopytsia.openbanking.dto.TransactionDto;

import java.util.List;

public class MockBankData {

    public record Account(String iban, BalanceAmount balance, List<TransactionDto> transactions) {
        public BalanceResponse toBalanceResponse() {
            return new BalanceResponse(iban, balance.amount(), balance.currency());
        }
    }

    public record BalanceAmount(java.math.BigDecimal amount, String currency) {}

    public record Root(List<Account> accounts) {}
}
