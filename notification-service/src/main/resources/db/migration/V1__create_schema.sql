CREATE TABLE notification_log (
    id                   UUID PRIMARY KEY,
    transfer_id          UUID                     NOT NULL,
    recipient_account_id UUID                     NOT NULL,
    kind                 VARCHAR(30)              NOT NULL,
    message              VARCHAR(500)             NOT NULL,
    sent_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    -- idempotência: um evento reentregue não gera notificação duplicada
    CONSTRAINT uk_notification_transfer_recipient UNIQUE (transfer_id, recipient_account_id)
);

CREATE INDEX idx_notification_recipient ON notification_log (recipient_account_id, sent_at DESC);

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
