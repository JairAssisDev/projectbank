-- Write model / estado da Saga
CREATE TABLE transfer (
    id              UUID PRIMARY KEY,
    from_account_id UUID                     NOT NULL,
    to_account_id   UUID                     NOT NULL,
    amount          NUMERIC(19, 2)           NOT NULL,
    status          VARCHAR(30)              NOT NULL,
    failure_reason  VARCHAR(500),
    idempotency_key VARCHAR(100),
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_transfer_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfer_distinct_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX idx_transfer_status ON transfer (status);

-- Read model (CQRS)
CREATE TABLE transfer_view (
    id              UUID PRIMARY KEY,
    from_account_id UUID                     NOT NULL,
    to_account_id   UUID                     NOT NULL,
    amount          NUMERIC(19, 2)           NOT NULL,
    status          VARCHAR(30)              NOT NULL,
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_transfer_view_from ON transfer_view (from_account_id, created_at DESC);
CREATE INDEX idx_transfer_view_to ON transfer_view (to_account_id, created_at DESC);

-- Transactional Outbox
CREATE TABLE outbox_message (
    id           UUID PRIMARY KEY,
    aggregate_id UUID                     NOT NULL,
    message_type VARCHAR(100)             NOT NULL,
    exchange     VARCHAR(200)             NOT NULL,
    routing_key  VARCHAR(200)             NOT NULL,
    payload      TEXT                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts     INT                      NOT NULL DEFAULT 0,
    last_error   VARCHAR(1000)
);

CREATE INDEX idx_outbox_pending ON outbox_message (created_at) WHERE published_at IS NULL;

-- Auditoria de falhas definitivas (DLQ)
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
