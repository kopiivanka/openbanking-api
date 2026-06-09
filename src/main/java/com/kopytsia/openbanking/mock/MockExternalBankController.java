package com.kopytsia.openbanking.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopytsia.openbanking.dto.BalanceResponse;
import com.kopytsia.openbanking.dto.TransactionDto;
import com.kopytsia.openbanking.client.ExternalPaymentRequest;
import com.kopytsia.openbanking.client.ExternalPaymentResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process stand-in for a PSD2 / OpenBanking provider. Backed by a JSON
 * resource and an in-memory ledger so the app runs end-to-end without a real
 * upstream bank.
 */
@RestController
@RequestMapping("/mock/psd2")
public class MockExternalBankController {

    private final ObjectMapper objectMapper;
    private final Map<String, MockBankData.Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, ExternalPaymentResponse> submittedPayments = new ConcurrentHashMap<>();

    public MockExternalBankController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        var resource = new ClassPathResource("mock-bank-data.json");
        var root = objectMapper.readValue(resource.getInputStream(), MockBankData.Root.class);
        for (var account : root.accounts()) {
            accounts.put(account.iban(), account);
        }
    }

    @GetMapping("/accounts/{iban}/balance")
    public ResponseEntity<BalanceResponse> balance(@PathVariable String iban) {
        var account = accounts.get(iban);
        if (account == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(account.toBalanceResponse());
    }

    @GetMapping("/accounts/{iban}/transactions")
    public ResponseEntity<List<TransactionDto>> transactions(@PathVariable String iban,
                                                             @RequestParam(defaultValue = "10") int limit) {
        var account = accounts.get(iban);
        if (account == null) return ResponseEntity.notFound().build();
        var sorted = account.transactions().stream()
                .sorted(Comparator.comparing(TransactionDto::bookingDate).reversed())
                .limit(limit)
                .toList();
        return ResponseEntity.ok(sorted);
    }

    @PostMapping("/payments")
    public ResponseEntity<ExternalPaymentResponse> payment(@RequestBody ExternalPaymentRequest req) {
        if (!accounts.containsKey(req.debtorIban())) {
            return ResponseEntity.badRequest().build();
        }
        var ref = "EXT-" + UUID.randomUUID();
        var response = new ExternalPaymentResponse(ref, "ACCEPTED");
        submittedPayments.put(ref, response);
        return ResponseEntity.ok(response);
    }
}
