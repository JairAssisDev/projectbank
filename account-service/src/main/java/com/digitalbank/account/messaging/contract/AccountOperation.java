package com.digitalbank.account.messaging.contract;

/** Passo da Saga executado no account-service (contrato compartilhado via JSON). */
public enum AccountOperation {
    DEBIT,
    CREDIT,
    REFUND
}
