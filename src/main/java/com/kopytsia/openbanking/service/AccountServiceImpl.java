package com.kopytsia.openbanking.service;

import com.kopytsia.openbanking.client.ExternalBankClient;
import com.kopytsia.openbanking.dto.BalanceResponse;
import com.kopytsia.openbanking.dto.TransactionDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private static final int RECENT_TRANSACTIONS_LIMIT = 10;

    private final ExternalBankClient bankClient;

    public AccountServiceImpl(ExternalBankClient bankClient) {
        this.bankClient = bankClient;
    }

    @Override
    public BalanceResponse getBalance(String iban) {
        return bankClient.fetchBalance(iban);
    }

    @Override
    public List<TransactionDto> getRecentTransactions(String iban) {
        return bankClient.fetchTransactions(iban, RECENT_TRANSACTIONS_LIMIT);
    }
}
