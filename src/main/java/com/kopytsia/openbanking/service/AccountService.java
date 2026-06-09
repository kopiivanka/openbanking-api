package com.kopytsia.openbanking.service;

import com.kopytsia.openbanking.dto.BalanceResponse;
import com.kopytsia.openbanking.dto.TransactionDto;

import java.util.List;

public interface AccountService {
    BalanceResponse getBalance(String iban);
    List<TransactionDto> getRecentTransactions(String iban);
}
