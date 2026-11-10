package com.digitalbank.transfer.command;

import java.math.BigDecimal;
import java.util.UUID;

/** Comando (lado Command do CQRS) para iniciar uma transferência. */
public record CreateTransferCommand(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey) {
}
