package com.digitalbank.transfer.infrastructure.messaging.contract;

import java.time.Instant;
import java.util.UUID;

/** Resposta de um participante da Saga (account-service). */
public record SagaReply(
        UUID messageId,
        UUID transferId,
        AccountOperation operation,
        boolean success,
        String failureReason,
        Instant occurredAt) {
}
