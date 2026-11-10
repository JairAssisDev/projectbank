package com.digitalbank.transfer.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Evento de domínio publicado quando a Saga termina com sucesso. */
public record TransferCompletedEvent(
        UUID eventId,
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        Instant completedAt) {
}
