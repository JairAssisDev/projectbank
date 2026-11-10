package com.digitalbank.account.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Comando enviado pelo orquestrador (transfer-service). A operação é determinada pela fila
 * (account.debit / account.credit / account.refund).
 */
public record AccountOperationCommand(
        UUID messageId,
        UUID transferId,
        UUID accountId,
        BigDecimal amount,
        Instant issuedAt) {
}
