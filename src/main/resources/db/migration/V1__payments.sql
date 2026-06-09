CREATE TABLE payments (
    id                  UUID            PRIMARY KEY,
    debtor_iban         VARCHAR(34)     NOT NULL,
    creditor_iban       VARCHAR(34)     NOT NULL,
    amount              NUMERIC(19, 2)  NOT NULL,
    currency            CHAR(3)         NOT NULL,
    status              VARCHAR(16)     NOT NULL,
    external_reference  VARCHAR(64),
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_payments_debtor_iban ON payments (debtor_iban);
CREATE INDEX idx_payments_created_at  ON payments (created_at DESC);
