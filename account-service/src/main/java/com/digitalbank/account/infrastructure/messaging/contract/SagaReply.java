package com.digitalbank.account.infrastructure.messaging.contract;

import java.time.Instant;
import java.util.UUID;

/** Resposta ao orquestrador com o resultado de um passo da Saga. */
public record SagaReply(
        UUID messageId,
        UUID transferId,
        AccountOperation operation,
        boolean success,
        String failureReason,
        Instant occurredAt) {

    public static SagaReply success(UUID transferId, AccountOperation operation, Instant now) {
        return new SagaReply(UUID.randomUUID(), transferId, operation, true, null, now);
    }

    public static SagaReply failure(UUID transferId, AccountOperation operation, String reason, Instant now) {
        return new SagaReply(UUID.randomUUID(), transferId, operation, false, reason, now);
    }
}
