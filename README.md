# openbanking-api

Simple PSD2-style OpenBanking REST API on Spring Boot 3.4 / Java 21.

It exposes three endpoints that proxy to a mocked external bank, persists
payments to Postgres before sending them upstream, and is protected by OAuth2
client-credentials. A small embedded authorization server is included so the
app runs end-to-end out of the box.

Persistence is **jOOQ** with code generated from the running Postgres schema.
Schema changes are versioned as Flyway SQL migrations under
`src/main/resources/db/migration` and applied by Spring Boot at startup.

## Endpoints

| Method | Path                                    | Scope             |
| ------ | --------------------------------------- | ----------------- |
| GET    | `/api/accounts/{iban}/balance`          | `accounts:read`   |
| GET    | `/api/accounts/{iban}/transactions`     | `accounts:read`   |
| POST   | `/api/payments/initiate`                | `payments:write`  |

Bonus endpoints:

| Method | Path                  | Purpose                            |
| ------ | --------------------- | ---------------------------------- |
| POST   | `/oauth2/token`       | Issue JWT (client_credentials)     |
| GET    | `/swagger-ui.html`    | OpenAPI UI                         |
| `*`    | `/mock/psd2/**`       | In-process mock external bank      |

## Run

Start Postgres (host port `5433`) and run the app:

```bash
docker compose up -d
./gradlew bootRun
```

The jOOQ-generated sources are committed under `src/generated/java`, so a fresh
clone builds without needing Postgres running for codegen. Regenerate only
when the schema changes (see below).

Spring Boot applies Flyway migrations on startup, so the schema is created
automatically the first time. On first run the `PaymentSeeder` inserts 5
sample payments (one per status) so the `payments` table is not empty.

### Adding a schema change

1. Drop a new `VN__description.sql` file in `src/main/resources/db/migration`.
2. Restart the app — Spring Flyway applies the migration to the dev DB.
3. Run `./gradlew generateJooq` to refresh the typed classes in
   `src/generated/java` against the live schema, and commit those.

### Database access

| Field    | Value                                |
| -------- | ------------------------------------ |
| Host     | `localhost`                          |
| Port     | `5433` (mapped from the container)   |
| Database | `openbanking`                        |
| User     | `openbanking`                        |
| Password | `openbanking`                        |
| JDBC URL | `jdbc:postgresql://localhost:5433/openbanking` |

Quick shell access:

```bash
docker exec -it openbanking-postgres psql -U openbanking -d openbanking
# then: SELECT id, debtor_iban, amount, status FROM payments ORDER BY created_at;
```

## Try it

Get a token:

```bash
TOKEN=$(curl -s -u openbanking-client:openbanking-secret \
  -d 'grant_type=client_credentials&scope=accounts:read payments:write' \
  http://localhost:8080/oauth2/token | jq -r .access_token)
```

Read a balance / transactions (uses a pre-seeded IBAN from the mock bank):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/accounts/DE89370400440532013000/balance

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/accounts/DE89370400440532013000/transactions
```

Initiate a payment:

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "debtorIban":"DE89370400440532013000",
        "creditorIban":"DE02500105170137075030",
        "amount":42.50,
        "currency":"EUR"
      }' \
  http://localhost:8080/api/payments/initiate
```

## How payments are handled

`PaymentServiceImpl` does, in order:

1. Fetch the debtor's balance from the external bank.
2. Reject with `422 currency_mismatch` if currencies differ.
3. If funds are insufficient: persist a `REJECTED` payment row (audit trail)
   and respond `422 insufficient_funds`.
4. Otherwise persist a `PENDING` payment, then call the external bank.
5. On success: update the row to `COMPLETED` with the external reference.
6. On external failure: update the row to `FAILED` with the error, respond
   `502 external_bank_error`.

So every payment attempt ends up in the local DB in a terminal state, and the
external call never happens before the local record exists.

## Package structure

```
com.kopytsia.openbanking
├── controller/    REST controllers, GlobalExceptionHandler, ApiError
├── service/       PaymentService / AccountService interfaces + Impl classes
├── repository/    Payment (domain object), PaymentStatus, PaymentRepository (jOOQ)
├── client/        ExternalBankClient, WebClient config, request/response DTOs
├── exception/     All application exceptions
├── dto/           API request/response records (PaymentRequest, PaymentResponse, …)
├── mock/          MockExternalBankController, PaymentSeeder
├── security/      OAuth2 Authorization Server + Resource Server config
└── config/        OpenAPI / Swagger config
```

## External bank

Configured by `openbanking.external.base-url` (default
`http://localhost:8080/mock/psd2`). The default points at the in-process
`MockExternalBankController` which serves balances and transactions from
`src/main/resources/mock-bank-data.json`. Override the property to point at
a real PSD2 sandbox or a WireMock instance.

## Tests

```bash
./gradlew test
```

Tests are self-contained — `OpenBankingIntegrationTest` boots a Postgres
container via Testcontainers and applies the Flyway migrations into it, so
they exercise the same SQL the app runs against in production. Docker must be
running, but the dev-side `docker compose` stack does **not** need to be up.

- `PaymentServiceTest` — Mockito unit tests covering the success, insufficient
  funds, currency mismatch, and external-failure paths.
- `OpenBankingIntegrationTest` — boots the full app with a random port, stubs
  the external bank with WireMock, gets a real JWT from the embedded auth
  server, and exercises every endpoint end-to-end.

## Configuration

Key properties in `application.yml`:

```yaml
openbanking:
  external:
    base-url: http://localhost:8080/mock/psd2
    connect-timeout-ms: 2000
    read-timeout-ms: 5000
  oauth:
    client-id: openbanking-client
    client-secret: openbanking-secret
```

The OAuth client and the in-memory JWKs are demo-only. Replace the
`RegisteredClientRepository` and `JWKSource` beans for any real deployment.
