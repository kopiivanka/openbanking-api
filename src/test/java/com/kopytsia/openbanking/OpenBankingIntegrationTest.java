package com.kopytsia.openbanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.kopytsia.openbanking.dto.PaymentRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;

import com.github.tomakehurst.wiremock.client.WireMock;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:${server.port}",
                "openbanking.seed.enabled=false"
        })
@AutoConfigureMockMvc
@Testcontainers
class OpenBankingIntegrationTest {

    private static final String DEBTOR = "DE89370400440532013000";
    private static final String CREDITOR = "DE02500105170137075030";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("openbanking")
            .withUsername("openbanking")
            .withPassword("openbanking");

    private static final WireMockServer WIREMOCK = new WireMockServer(0);

    @BeforeAll
    static void startWireMock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("openbanking.external.base-url", () -> "http://localhost:" + WIREMOCK.port());
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private org.springframework.core.env.Environment env;

    @Test
    void rejects_unauthenticated_requests() throws Exception {
        mockMvc.perform(get("/api/accounts/{iban}/balance", DEBTOR))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns_balance_from_external_bank() throws Exception {
        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/accounts/" + DEBTOR + "/balance"))
                .willReturn(WireMock.okJson("""
                        {"iban":"%s","amount":1500.00,"currency":"EUR"}
                        """.formatted(DEBTOR))));

        String token = obtainToken("accounts:read");

        mockMvc.perform(get("/api/accounts/{iban}/balance", DEBTOR)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban", is(DEBTOR)))
                .andExpect(jsonPath("$.amount", is(1500.00)))
                .andExpect(jsonPath("$.currency", is("EUR")));
    }

    @Test
    void returns_recent_transactions() throws Exception {
        WIREMOCK.stubFor(WireMock.get(WireMock.urlPathEqualTo("/accounts/" + DEBTOR + "/transactions"))
                .willReturn(WireMock.okJson("""
                        [
                          {"id":"t1","bookingDate":"2026-06-08T10:00:00Z","amount":-10.00,"currency":"EUR","counterpartyIban":"%s","description":"a"},
                          {"id":"t2","bookingDate":"2026-06-07T10:00:00Z","amount":-20.00,"currency":"EUR","counterpartyIban":"%s","description":"b"}
                        ]
                        """.formatted(CREDITOR, CREDITOR))));

        String token = obtainToken("accounts:read");

        mockMvc.perform(get("/api/accounts/{iban}/transactions", DEBTOR)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].id", is("t1")));
    }

    @Test
    void initiates_payment_when_funds_available() throws Exception {
        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/accounts/" + DEBTOR + "/balance"))
                .willReturn(WireMock.okJson("""
                        {"iban":"%s","amount":500.00,"currency":"EUR"}
                        """.formatted(DEBTOR))));
        WIREMOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/payments"))
                .willReturn(WireMock.okJson("""
                        {"externalReference":"EXT-ABC","status":"ACCEPTED"}
                        """)));

        String token = obtainToken("payments:write");

        var body = objectMapper.writeValueAsString(
                new PaymentRequest(DEBTOR, CREDITOR, new BigDecimal("100.00"), "EUR"));

        mockMvc.perform(post("/api/payments/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.externalReference", is("EXT-ABC")))
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    void rejects_payment_when_insufficient_funds() throws Exception {
        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/accounts/" + DEBTOR + "/balance"))
                .willReturn(WireMock.okJson("""
                        {"iban":"%s","amount":5.00,"currency":"EUR"}
                        """.formatted(DEBTOR))));

        String token = obtainToken("payments:write");

        var body = objectMapper.writeValueAsString(
                new PaymentRequest(DEBTOR, CREDITOR, new BigDecimal("100.00"), "EUR"));

        mockMvc.perform(post("/api/payments/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("insufficient_funds")));
    }

    private String obtainToken(String scope) throws Exception {
        String cid = env.getProperty("openbanking.oauth.client-id");
        String secret = env.getProperty("openbanking.oauth.client-secret");
        String basic = Base64.getEncoder().encodeToString((cid + ":" + secret).getBytes());

        var result = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("access_token");
    }
}
