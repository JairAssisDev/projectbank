package com.digitalbank.account.domain.model;

/**
 * Resultado de uma operação de saldo disparada pela Saga.
 * Falhas de negócio são valores (não exceções): não devem ser retentadas nem ir para a DLQ.
 */
public sealed interface OperationResult {

    record Success() implements OperationResult {
    }

    record Failure(FailureReason reason) implements OperationResult {
    }

    static OperationResult success() {
        return new Success();
    }

    static OperationResult failure(FailureReason reason) {
        return new Failure(reason);
    }

    enum FailureReason {
        ACCOUNT_NOT_FOUND,
        INSUFFICIENT_FUNDS
    }
}
