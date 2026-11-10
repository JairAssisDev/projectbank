CREATE TABLE account (
    id          UUID PRIMARY KEY,
    owner_name  VARCHAR(120)             NOT NULL,
    document    VARCHAR(20)              NOT NULL,
    balance     NUMERIC(19, 2)           NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_account_document UNIQUE (document),
    -- defesa em profundidade: mesmo que algum código esqueça a regra, o banco recusa saldo negativo
    CONSTRAINT ck_account_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE account_ledger (
    id          UUID PRIMARY KEY,
    account_id  UUID                     NOT NULL REFERENCES account (id),
    transfer_id UUID                     NOT NULL,
    entry_type  VARCHAR(10)              NOT NULL,
    amount      NUMERIC(19, 2)           NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    -- idempotência dos passos da Saga
    CONSTRAINT uk_ledger_transfer_entry UNIQUE (transfer_id, account_id, entry_type),
    CONSTRAINT ck_ledger_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_account_created ON account_ledger (account_id, created_at);

CREATE TABLE error_audit (
    id                   UUID PRIMARY KEY,
    dead_letter_queue    VARCHAR(200),
    original_exchange    VARCHAR(200),
    original_routing_key VARCHAR(200),
    message_id           VARCHAR(100),
    payload              TEXT,
    exception_message    TEXT,
    stacktrace           TEXT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_error_audit_created ON error_audit (created_at);
