package com.digitalbank.transfer.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Comando para o account-service; a operação é definida pela routing key (account.debit|credit|refund). */
public record AccountOperationCommand(
        UUID messageId,
        UUID transferId,
        UUID accountId,
        BigDecimal amount,
        Instant issuedAt) {
}
