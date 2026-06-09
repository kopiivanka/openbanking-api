package com.kopytsia.openbanking.controller;

import com.kopytsia.openbanking.dto.BalanceResponse;
import com.kopytsia.openbanking.dto.TransactionDto;
import com.kopytsia.openbanking.service.AccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{accountId}/transactions")
    public List<TransactionDto> transactions(@PathVariable String accountId) {
        return accountService.getRecentTransactions(accountId);
    }
}
