package com.kopytsia.openbanking.client;

import com.kopytsia.openbanking.dto.BalanceResponse;
import com.kopytsia.openbanking.dto.TransactionDto;
import com.kopytsia.openbanking.exception.AccountNotFoundException;
import com.kopytsia.openbanking.exception.ExternalBankException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

@Component
public class ExternalBankClient {

    private final WebClient client;
    private final Duration timeout;

    public ExternalBankClient(WebClient externalBankWebClient, ExternalBankProperties props) {
        this.client = externalBankWebClient;
        this.timeout = Duration.ofMillis(props.readTimeoutMs());
    }

    public BalanceResponse fetchBalance(String iban) {
        return get("/accounts/{iban}/balance", BalanceResponse.class, iban);
    }

    public List<TransactionDto> fetchTransactions(String iban, int limit) {
        try {
            return client.get()
                    .uri(uri -> uri.path("/accounts/{iban}/transactions")
                            .queryParam("limit", limit)
                            .build(iban))
                    .retrieve()
                    .bodyToFlux(TransactionDto.class)
                    .collectList()
                    .block(timeout);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) throw new AccountNotFoundException(iban);
            throw new ExternalBankException("Failed to fetch transactions: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            throw new ExternalBankException("Failed to fetch transactions", e);
        }
    }

    public ExternalPaymentResponse submitPayment(ExternalPaymentRequest req) {
        try {
            return client.post()
                    .uri("/payments")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(ExternalPaymentResponse.class)
                    .block(timeout);
        } catch (WebClientResponseException e) {
            throw new ExternalBankException(
                    "Payment rejected by external bank: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            throw new ExternalBankException("Failed to submit payment", e);
        }
    }

    private <T> T get(String uriTemplate, Class<T> responseType, Object... uriVars) {
        try {
            return client.get()
                    .uri(uriTemplate, uriVars)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block(timeout);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) throw new AccountNotFoundException(String.valueOf(uriVars[0]));
            throw new ExternalBankException("External bank error: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            throw new ExternalBankException("Failed to call external bank", e);
        }
    }
}
