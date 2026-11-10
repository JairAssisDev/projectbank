package com.digitalbank.transfer.domain;

/**
 * Estados da Saga de transferência.
 *
 * <pre>
 * PENDING ──debit ok──▶ DEBITED ──credit ok──▶ COMPLETED
 *    │                     │
 *    └─debit falhou─▶ FAILED   └─credit falhou─▶ COMPENSATING ──refund ok──▶ COMPENSATED
 *                                                     └──refund falhou──▶ COMPENSATION_FAILED
 * </pre>
 */
public enum TransferStatus {
    PENDING,
    DEBITED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED,
    COMPENSATION_FAILED;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, FAILED, COMPENSATED, COMPENSATION_FAILED -> true;
            case PENDING, DEBITED, COMPENSATING -> false;
        };
    }
}
