package com.digitalbank.account.domain.model;

public enum LedgerEntryType {
    /** Saída de valor da conta de origem. */
    DEBIT,
    /** Entrada de valor na conta de destino. */
    CREDIT,
    /** Estorno (compensação da Saga) devolvendo o valor à conta de origem. */
    REFUND
}
